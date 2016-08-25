package ice.http.server.remote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
import ice.http.server.action.Action;
import ice.http.server.action.InterceptorManager;
import ice.http.server.action.MethodAction;
import ice.http.server.annotations.Method.HttpMethod;
import ice.http.server.parser.Parser;
import ice.http.server.remote.annotations.Expose;
import ice.http.server.remote.annotations.Namespace;
import ice.http.server.router.Router;
import ice.http.server.utils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class RemoteRouter implements SettingsAware, Router, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private InterceptorManager interceptorManager;
	private Map<String, Object> namespacedServices;
	private final Map<String, List<Action>> routes = Maps.newHashMap();
	private final Logger logger = LoggerFactory.getLogger(RemoteRouter.class);
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Action route(Request request) {
		String requestBody = (String) request.args.get(Parser.BODY);
		boolean useParameterNames = (Boolean) request.args.get(RemoteRequestHandler.USE_PARAMETER_NAMES);

		JsonNode rootNode;

		try {
			rootNode = MAPPER.readTree(requestBody);
		} catch (IOException e) {
			return null;
		}

		if (useParameterNames && !rootNode.isObject()) {
			throw new UnsupportedOperationException("parameter should be map");
		}

		if (!useParameterNames && !rootNode.isArray()) {
			throw new UnsupportedOperationException("parameter should be array or collection");
		}

		List<Action> actions = routes.get(request.path);

		if (actions == null) {
			return null;
		}

		return useParameterNames ? routeWithParameterNames(actions, rootNode) : routeWithoutParameterNames(actions, rootNode);
	}

	private boolean matches(JsonNode jsonNode, Parameter parameter) {
		Type type = (parameter.type instanceof ParameterizedType) ? parameter.type : parameter.clazz;

		if (type == boolean.class) {
			return jsonNode.isBoolean();
		}

		if (type == Boolean.class) {
			return jsonNode.isBoolean() || jsonNode.isNull();
		}

		if (type == byte.class || type == short.class) {
			return jsonNode.isNumber();
		}

		if (type == Byte.class || type == Short.class) {
			return jsonNode.isNumber() || jsonNode.isNull();
		}

		if (type == int.class) {
			return jsonNode.isNumber();
		}

		if (type == Integer.class) {
			return jsonNode.isNumber() || jsonNode.isNull();
		}

		if (type == long.class) {
			return jsonNode.isNumber();
		}

		if (type == Long.class) {
			return jsonNode.isNumber() || jsonNode.isNull();
		}

		if (type == String.class) {
			return jsonNode.isTextual() || jsonNode.isNull();
		}

		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;

			if (clazz.isArray()) {
				return jsonNode.isArray();
			}

			return jsonNode.isObject();
		}

		return true;
	}

	private boolean parameterEquals(Set<String> set1, Set<String> set2) {
		if (set1 == null && set2 == null) {
			return true;
		}

		if (set1 == null || set2 == null) {
			return false;
		}

		if (set1.size() != set2.size()) {
			return false;
		}

		for (String el : set1) {
			if (!set2.contains(el)) {
				return false;
			}
		}

		return true;
	}

	private Action routeWithParameterNames(List<Action> actions, JsonNode rootNode) {
		Map<String, JsonNode> fieldMap = Maps.newLinkedHashMap();
		Iterator<Entry<String, JsonNode>> fields = rootNode.fields();

		while (fields.hasNext()) {
			Entry<String, JsonNode> field = fields.next();
			fieldMap.put(field.getKey(), field.getValue());
		}

		for (Action action : actions) {
			MethodAction methodAction = (MethodAction) action;
			Map<String, Parameter> parameters = methodAction.parameters();

			if (!parameterEquals(parameters.keySet(), fieldMap.keySet())) {
				continue;
			}

			boolean matches = true;

			for (Entry<String, Parameter> entry : parameters.entrySet()) {
				JsonNode jsonNode = fieldMap.get(entry.getKey());

				if (!matches(jsonNode, entry.getValue())) {
					matches = false;
					break;
				}
			}

			if (matches) {
				return action;
			}
		}

		return new InvalidAction();
	}

	private Action routeWithoutParameterNames(List<Action> actions, JsonNode rootNode) {
		List<JsonNode> jsonNodes = Lists.newArrayList();
		Iterator<JsonNode> jsonNodeIterator = rootNode.elements();

		while (jsonNodeIterator.hasNext()) {
			jsonNodes.add(jsonNodeIterator.next());
		}

		for (Action action : actions) {
			MethodAction methodAction = (MethodAction) action;
			Map<String, Parameter> parameters = methodAction.parameters();

			if (parameters.size() != jsonNodes.size()) {
				continue;
			}

			int i = 0;
			boolean matches = true;

			for (Entry<String, Parameter> entry : parameters.entrySet()) {
				if (!matches(jsonNodes.get(i), entry.getValue())) {
					matches = false;
					break;
				}

				i++;
			}

			if (matches) {
				return action;
			}
		}

		return new InvalidAction();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

		for (Entry<String, Object> entry : namespacedServices.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();

			try {
				clazz = ((Advised) entry.getValue()).getTargetSource().getTarget().getClass();
			} catch (Exception ignored) {
			}

			Namespace namespace = clazz.getAnnotation(Namespace.class);
			logger.debug("clazz={}, namespace={}", clazz.getName(), namespace);

			if (namespace == null || "".equals(namespace.value())) {
				logger.debug("namespace is null or empty. clazz={}", clazz.getName());
				continue;
			}

			String prefix = namespace.value();
			boolean exposeAll = namespace.exposeAll();

			for (Method method : clazz.getDeclaredMethods()) {
				logger.debug("clazz={}, method={}", clazz.getName(), method.getName());

				if (!Modifier.isPublic(method.getModifiers())) {
					logger.debug("method is not public. clazz={}, method={}", clazz.getName(), method.getName());
					continue;
				}

				Expose expose = method.getAnnotation(Expose.class);

				if (!exposeAll && expose == null) {
					logger.debug("exposeAll is false and @Expose is not found. clazz={}, method={}", clazz.getName(), method.getName());
					continue;
				}

				String path = "/" + prefix + "/" + (expose == null || StringUtils.isBlank(expose.value()) ? method.getName() : expose.value());

				Class<?>[] parameterTypes = method.getParameterTypes();
				Type[] genericParameterTypes = method.getGenericParameterTypes();
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();
				String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);

				Map<String, Parameter> parameters = Maps.newLinkedHashMap();

				for (int i = 0; i < parameterTypes.length; i++) {
					parameters.put(parameterNames[i], new Parameter(genericParameterTypes[i], parameterTypes[i], parameterNames[i], parameterNames[i], true, parameterAnnotations[i], null, null));
				}

				MethodAction methodAction = new MethodAction(entry.getValue(), method, HttpMethod.POST, path, parameters);
				interceptorManager.addInterceptors(methodAction);

				logger.debug("[remote] {} => {}", new Object[]{path, methodAction.bean().getClass().getSimpleName() + "#" + methodAction.method().getName()});

				List<Action> actions = routes.get(path);

				if (actions == null) {
					actions = Lists.newArrayList();
				}

				actions.add(methodAction);
				routes.put(path, actions);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.namespacedServices = applicationContext.getBeansWithAnnotation(Namespace.class);
		this.interceptorManager = BeanUtils.getBean(settings, applicationContext, InterceptorManager.class);
	}
}
