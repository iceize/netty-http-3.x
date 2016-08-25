package ice.http.server.router;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import ice.http.server.*;
import ice.http.server.action.Action;
import ice.http.server.action.InterceptorManager;
import ice.http.server.action.MethodAction;
import ice.http.server.annotations.Method.HttpMethod;
import ice.http.server.annotations.Param;
import ice.http.server.utils.BeanUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Deprecated
public class PatternRouter implements SettingsAware, Router, InitializingBean, ApplicationContextAware {
	private static final String BOOLEAN_PATTERN = "true|false";
	private static final String DECIMAL_PATTERN = "[+-]?[0-9]+";
	private static final String FLOAT_PATTERN = "[+-]?[0-9]*\\.?[0-9]+";
	private static final String STRING_PATTERN = "[^/]+";

	private Settings settings;
	private Map<String, Object> controllers;
	private InterceptorManager interceptorManager;
	private Map<Class<?>, String> regexs;
	private ConcurrentLinkedHashMap<String, Action> staticRouteCaches;
	private final Map<HttpMethod, Map<String, Action>> directRoutes = Maps.newHashMap();
	private final Map<HttpMethod, Map<Pattern, Action>> patternRoutes = Maps.newHashMap();
	private final ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
	private final Logger logger = LoggerFactory.getLogger(PatternRouter.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Action route(Request request) {
		String routeKey = request.method.name() + " " + request.path;

		Map<String, Action> directRouteMap = directRoutes.get(request.method);

		if (directRouteMap != null) {
			Action action = directRouteMap.get(request.path);

			if (action != null) {
				staticRouteCaches.putIfAbsent(routeKey, action);
				return action;
			}
		}

		Map<Pattern, Action> patternRouteMap = patternRoutes.get(request.method);

		if (patternRouteMap != null) {
			for (Entry<Pattern, Action> entry : patternRouteMap.entrySet()) {
				if (entry.getKey().matcher(request.path).find()) {
					return entry.getValue();
				}
			}
		}

		return null;
	}

	private List<Annotation> getMvcAnnotations(Method method) {
		List<Annotation> annotations = Lists.newArrayList();

		for (Class<? extends Annotation> annotationClass : Context.getMvcAnnotations().keySet()) {
			Annotation annotation = method.getAnnotation(annotationClass);

			if (annotation != null) {
				annotations.add(annotation);
			}
		}

		return annotations;
	}

	private Set<String> getPathVariables(String path) {
		Matcher matcher = Context.PATH_VARIABLE_PATTERN.matcher(path);
		Set<String> variables = Sets.newHashSet();

		while (matcher.find()) {
			variables.add(matcher.group(1));
		}

		return variables;
	}

	private Map<String, Parameter> getParameters(Method method) {
		Class<?>[] parameterTypes = method.getParameterTypes();

		if (parameterTypes == null || parameterTypes.length == 0) {
			return null;
		}

		Type[] genericParameterTypes = method.getGenericParameterTypes();
		Annotation[][] parameterAnnotations = method.getParameterAnnotations();
		String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);

		if (parameterTypes.length != parameterNames.length) {
			return null;
		}

		Map<String, Parameter> parameterMap = Maps.newLinkedHashMap();

		for (int i = 0; i < parameterTypes.length; i++) {
			String parameterName = parameterNames[i];
			String defaultValue = null;

			boolean required = false;
			Class<?> convertTo = null;
			Annotation[] annotations = parameterAnnotations[i];

			if (annotations != null) {
				for (Annotation annotation : annotations) {
					if (annotation.annotationType() == Param.class) {
						Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
						String value = (String) attributes.get("value");

						if (StringUtils.isNotEmpty(value)) {
							parameterName = value;
						}

						if (!Param.DEFAULT_NONE.equals((String) attributes.get("defaultValue"))) {
							defaultValue = (String) attributes.get("defaultValue");
						}

						convertTo = (Class<?>) attributes.get("convertTo");
						required = MapUtils.getBooleanValue(attributes, "required", false);

						break;
					}
				}
			}

			parameterMap.put(parameterName, new Parameter(genericParameterTypes[i], parameterTypes[i], parameterNames[i], parameterName, required, parameterAnnotations[i], defaultValue, convertTo == null || convertTo == Class.class ? null : convertTo));
		}

		return parameterMap;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		regexs = Maps.newHashMap();
		regexs.put(boolean.class, BOOLEAN_PATTERN);
		regexs.put(Boolean.class, BOOLEAN_PATTERN);
		regexs.put(byte.class, DECIMAL_PATTERN);
		regexs.put(Byte.class, DECIMAL_PATTERN);
		regexs.put(short.class, DECIMAL_PATTERN);
		regexs.put(Short.class, DECIMAL_PATTERN);
		regexs.put(int.class, DECIMAL_PATTERN);
		regexs.put(Integer.class, DECIMAL_PATTERN);
		regexs.put(long.class, DECIMAL_PATTERN);
		regexs.put(Long.class, DECIMAL_PATTERN);
		regexs.put(float.class, FLOAT_PATTERN);
		regexs.put(Float.class, FLOAT_PATTERN);
		regexs.put(double.class, FLOAT_PATTERN);
		regexs.put(Double.class, FLOAT_PATTERN);
		regexs.put(String.class, STRING_PATTERN);

		int staticRouteCacheSize = Integer.parseInt(System.getProperty("cache.static.route", "1000"));
		staticRouteCaches = new ConcurrentLinkedHashMap.Builder<String, Action>().maximumWeightedCapacity(staticRouteCacheSize).build();

		ConcurrentMap<String, Pattern> patterns = Maps.newConcurrentMap();
		Map<Class<? extends Annotation>, HttpMethod> mvcAnnotations = Context.getMvcAnnotations();

		for (Entry<String, Object> entry : controllers.entrySet()) {
			Method[] methods = entry.getValue().getClass().getDeclaredMethods();

			for (Method method : methods) {
				if (!Modifier.isPublic(method.getModifiers())) {
					continue;
				}

				List<Annotation> annotations = getMvcAnnotations(method);

				if (CollectionUtils.isEmpty(annotations)) {
					continue;
				}

				for (Annotation annotation : annotations) {
					HttpMethod httpMethod = mvcAnnotations.get(annotation.annotationType());
					Map<String, Action> directRouteMap = directRoutes.get(httpMethod);
					Map<Pattern, Action> patternRouteMap = patternRoutes.get(httpMethod);

					if (directRouteMap == null) {
						directRouteMap = Maps.newHashMap();
					}

					if (patternRouteMap == null) {
						patternRouteMap = Maps.newLinkedHashMap();
					}

					Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
					String[] paths = (String[]) attributes.get("value");
					Map<String, Parameter> parameters = getParameters(method);

					for (String path : paths) {
						String pattern = path;
						boolean replaced = false;
						Set<String> pathVariables = getPathVariables(path);

						if (!CollectionUtils.isEmpty(pathVariables)) {
							if (parameters == null) {
								throw new IllegalArgumentException("invalid path variable: " + entry.getKey() + "#" + method.getName());
							}

							Set<String> parameterNames = parameters.keySet();

							if (!CollectionUtils.isEmpty(Sets.difference(pathVariables, parameterNames))) {
								throw new IllegalArgumentException("invalid path variable: " + entry.getKey() + "#" + method.getName());
							}

							for (String pathVariable : pathVariables) {
								Parameter parameter = parameters.get(pathVariable);

								if (!regexs.containsKey(parameter.clazz)) {
									throw new IllegalArgumentException("invalid path variable: " + entry.getKey() + "#" + method.getName());
								}

								String regex = regexs.get(parameter.clazz);
								pattern = StringUtils.replace(pattern, "{" + pathVariable + "}", "(" + regex + ")");
								replaced = true;
							}
						}

						MethodAction methodAction = new MethodAction(entry.getValue(), method, httpMethod, path, parameters);
						interceptorManager.addInterceptors(methodAction);

						if (replaced) { // pattern
							pattern = "^" + pattern + "$";
							patterns.putIfAbsent(pattern, Pattern.compile(pattern));
							patternRouteMap.put(patterns.get(pattern), methodAction);
							patternRoutes.put(httpMethod, patternRouteMap);
						} else {
							directRouteMap.put(pattern, methodAction);
							directRoutes.put(httpMethod, directRouteMap);
						}
					}
				}
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<HttpMethod, Map<String, Action>> entry : directRoutes.entrySet()) {
				for (Entry<String, Action> pathEntry : entry.getValue().entrySet()) {
					MethodAction action = (MethodAction) pathEntry.getValue();
					logger.debug("[direct] {} {} => {}", entry.getKey(), pathEntry.getKey(), action);
				}
			}

			for (Entry<HttpMethod, Map<Pattern, Action>> entry : patternRoutes.entrySet()) {
				for (Entry<Pattern, Action> pathEntry : entry.getValue().entrySet()) {
					MethodAction action = (MethodAction) pathEntry.getValue();
					logger.debug("[pattern] {} {} => {}#{}", entry.getKey(), pathEntry.getKey().pattern(), action.bean().getClass().getSimpleName(), action.method().getName());
				}
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.controllers = applicationContext.getBeansWithAnnotation(Controller.class);
		this.interceptorManager = BeanUtils.getBean(settings, applicationContext, InterceptorManager.class);
	}
}
