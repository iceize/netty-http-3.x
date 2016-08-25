package ice.http.server.router;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

public class TreeRouter implements SettingsAware, Router, PathExposure, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private Map<String, Object> controllers;
	private InterceptorManager interceptorManager;
	private final Map<HttpMethod, Tree> routes = Maps.newHashMap();
	private final Map<HttpMethod, Map<String, Action>> paths = Maps.newTreeMap();
	private final ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
	private final Logger logger = LoggerFactory.getLogger(TreeRouter.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Action route(Request request) {
		Tree tree = routes.get(request.method);
		return Tree.find(tree, request.path.substring(1));
	}

	@Override
	public Map<HttpMethod, Map<String, Action>> getPaths() {
		return paths;
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
					Tree tree = routes.get(httpMethod);

					if (tree == null) {
						tree = new Tree("/", null);
					}

					Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
					String[] paths = (String[]) attributes.get("value");
					Map<String, Parameter> parameters = getParameters(method);

					for (String path : paths) {
						MethodAction methodAction = new MethodAction(entry.getValue(), method, httpMethod, path, parameters);
						interceptorManager.addInterceptors(methodAction);
						Tree.generate(tree, path.substring(1), methodAction);

						Map<String, Action> pathMap = this.paths.get(httpMethod);

						if (pathMap == null) {
							pathMap = Maps.newTreeMap();
						}

						pathMap.put(path, methodAction);
						this.paths.put(httpMethod, pathMap);

						logger.debug("[tree] {} {} => {}", httpMethod.name(), path, methodAction.bean().getClass().getSimpleName() + "#" + methodAction.method().getName());
					}

					routes.put(httpMethod, tree);
				}
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.controllers = applicationContext.getBeansWithAnnotation(Controller.class);
		this.interceptorManager = BeanUtils.getBean(settings, applicationContext, InterceptorManager.class);
	}

	static class Tree {
		final Map<String, Tree> children = Maps.newLinkedHashMap();
		String name;
		Action action;

		Tree(String name, Action action) {
			this.name = name;
			this.action = action;
		}

		static void generate(Tree tree, String path, Action action) {
			int index = path.indexOf("/");
			String name = index < 0 ? path : path.substring(0, index);

			if ("".equals(name)) {
				tree.action = action;
				return;
			}

			if (name.startsWith("{") && name.endsWith("}")) {
				name = "*";
			}

			Tree childTree = tree.children.get(name);

			if (index < 0) { // leaf
				if (childTree == null) {
					childTree = new Tree(name, action);
				} else {
					childTree.action = action;
				}

				tree.children.put(name, childTree);
				return;
			} else {
				if (childTree == null) {
					childTree = new Tree(name, null);
				}

				tree.children.put(name, childTree);
			}

			generate(tree.children.get(name), path.substring(index + 1), action);
		}

		static Action find(Tree tree, String path) {
			if (tree == null) {
				return null;
			}

			if ("".equals(path)) {
				return tree.action;
			}

			if (path == null) {
				return null;
			}

			String[] splits = path.split("/", 2);
			Tree subtree = tree.children.get(splits[0]);

			if (subtree == null) {
				subtree = tree.children.get("*");
			}

			if (splits.length < 2) {
				return subtree == null ? null : subtree.action;
			}

			return find(subtree, splits[1]);
		}
	}
}
