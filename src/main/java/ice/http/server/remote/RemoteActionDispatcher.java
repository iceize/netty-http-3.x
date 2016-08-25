package ice.http.server.remote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.*;
import ice.http.server.action.Action;
import ice.http.server.action.Interceptor;
import ice.http.server.action.InterceptorManager;
import ice.http.server.action.MethodAction;
import ice.http.server.annotations.After;
import ice.http.server.annotations.Before;
import ice.http.server.annotations.Catch;
import ice.http.server.annotations.Finally;
import ice.http.server.dispatcher.MethodActionDispatcher;
import ice.http.server.parser.Parser;
import ice.http.server.utils.BeanUtils;
import ice.http.server.view.View;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;

public class RemoteActionDispatcher extends MethodActionDispatcher implements SettingsAware, ApplicationContextAware {
	private Settings settings;
	private InterceptorManager interceptorManager;
	private static final View jsonView = new RemoteJson();
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Class<? extends Action> assignableFrom() {
		return MethodAction.class;
	}

	private <T> Object invokeWithParameters(MethodAction methodAction, JsonNode rootNode) {
		Map<String, JsonNode> fieldMap = Maps.newLinkedHashMap();
		Iterator<Entry<String, JsonNode>> fields = rootNode.fields();

		while (fields.hasNext()) {
			Entry<String, JsonNode> field = fields.next();
			fieldMap.put(field.getKey(), field.getValue());
		}

		Map<String, Object> parameterValues = Maps.newLinkedHashMap();

		for (Entry<String, Parameter> entry : methodAction.parameters().entrySet()) {
			Type type = (entry.getValue().type instanceof ParameterizedType) ? entry.getValue().type : entry.getValue().clazz;

			try {
				parameterValues.put(entry.getKey(), MAPPER.readValue(fieldMap.get(entry.getKey()).toString(), new RemoteTypeReference<T>(type)));
			} catch (IOException e) {
				parameterValues.put(entry.getKey(), null);
			}
		}

		return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean(), parameterValues.values().toArray());
	}

	private <T> Object invokeWithoutParameters(MethodAction methodAction, JsonNode rootNode) {
		List<JsonNode> jsonNodes = Lists.newArrayList();
		Iterator<JsonNode> jsonNodeIterator = rootNode.elements();

		while (jsonNodeIterator.hasNext()) {
			jsonNodes.add(jsonNodeIterator.next());
		}

		int i = 0;
		Map<String, Object> parameterValues = Maps.newLinkedHashMap();

		for (Entry<String, Parameter> entry : methodAction.parameters().entrySet()) {
			Type type = (entry.getValue().type instanceof ParameterizedType) ? entry.getValue().type : entry.getValue().clazz;

			try {
				parameterValues.put(entry.getKey(), MAPPER.readValue(jsonNodes.get(i).toString(), new RemoteTypeReference<T>(type)));
			} catch (IOException e) {
				parameterValues.put(entry.getKey(), null);
			}

			i++;
		}

		return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean(), parameterValues.values().toArray());
	}

	@Override
	protected Entry<Object, View> dispatch(Request request, Response response, MethodAction methodAction) {
		boolean useParameterNames = (boolean) request.args.get(RemoteRequestHandler.USE_PARAMETER_NAMES);
		Map<Class<? extends Annotation>, Set<Interceptor>> interceptors = methodAction.interceptors();

		try {
			JsonNode rootNode = MAPPER.readTree((String) request.args.get(Parser.BODY));
			interceptorManager.intercept(methodAction, interceptors.get(Before.class), request, response);
			Object result = useParameterNames ? invokeWithParameters(methodAction, rootNode) : invokeWithoutParameters(methodAction, rootNode);
			interceptorManager.intercept(methodAction, interceptors.get(After.class), request, response);
			return new AbstractMap.SimpleImmutableEntry<>(result, jsonView);
		} catch (Exception e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			Interceptor interceptor = interceptorManager.findProperInterceptor(interceptors.get(Catch.class), cause);

			if (interceptor == null) {
				throw new RemoteActionException(request, e);
			}

			Object result = interceptorManager.intercept(interceptor, request, response, cause);
			return new AbstractMap.SimpleImmutableEntry<>(result, jsonView);
		} finally {
			interceptorManager.intercept(methodAction, interceptors.get(Finally.class), request, response);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.interceptorManager = BeanUtils.getBean(settings, applicationContext, InterceptorManager.class);
	}
}
