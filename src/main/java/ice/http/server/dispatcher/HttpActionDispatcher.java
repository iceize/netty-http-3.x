package ice.http.server.dispatcher;

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
import ice.http.server.annotations.Method.HttpMethod;
import ice.http.server.binder.BinderManager;
import ice.http.server.utils.BeanUtils;
import ice.http.server.view.View;
import ice.http.server.view.ViewResolver;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;

public class HttpActionDispatcher extends MethodActionDispatcher implements SettingsAware, ApplicationContextAware {
	private Settings settings;
	private BinderManager binderManager;
	private ViewResolver viewResolver;
	private InterceptorManager interceptorManager;

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Class<? extends Action> assignableFrom() {
		return MethodAction.class;
	}

	private Map<String, List<String>> getPathVariables(MethodAction methodAction, String requestPath) {
		String[] paths = StringUtils.split(methodAction.path(), "/");
		String[] requestPaths = StringUtils.split(requestPath, "/");
		Map<String, List<String>> pathVariables = Maps.newHashMap();

		for (int i = 0; i < paths.length; i++) {
			Matcher matcher = Context.PATH_VARIABLE_PATTERN.matcher(paths[i]);

			if (matcher.find()) {
				pathVariables.put(matcher.group(1), Lists.newArrayList(requestPaths[i]));
			}
		}

		return pathVariables;
	}

	private Object invoke(MethodAction methodAction, Request request, Response response) {
		if (CollectionUtils.isEmpty(methodAction.parameters())) {
			return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean());
		}

		List<Object> args = Lists.newArrayList();
		Map<String, List<String>> requestParams = Maps.newHashMap(request.params);
		requestParams.putAll(getPathVariables(methodAction, request.path));

		for (Entry<String, Parameter> entry : methodAction.parameters().entrySet()) {
			args.add(binderManager.bind(request, response, entry.getValue(), requestParams));
		}

		return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean(), args.toArray(new Object[args.size()]));
	}

	private Entry<Object, View> getModelAndView(Object result) {
		ModelAndView modelAndView = (ModelAndView) result;
		return new AbstractMap.SimpleImmutableEntry<>(modelAndView.model, viewResolver.resolve(modelAndView.view));
	}

	private Entry<Object, View> getModelAndView(Object result, HttpMethod httpMethod, Method method) {
		if (result != null && (result instanceof ModelAndView)) {
			return getModelAndView(result);
		}

		return new AbstractMap.SimpleImmutableEntry<>(result, viewResolver.resolve(httpMethod, method));
	}

	private Entry<Object, View> getModelAndView(Object result, Class<? extends View> view) {
		if (result != null && (result instanceof ModelAndView)) {
			return getModelAndView(result);
		}

		return new AbstractMap.SimpleImmutableEntry<>(result, viewResolver.resolve(view));
	}

	@Override
	protected Entry<Object, View> dispatch(Request request, Response response, MethodAction methodAction) {
		Map<Class<? extends Annotation>, Set<Interceptor>> interceptors = methodAction.interceptors();

		try {
			interceptorManager.intercept(methodAction, interceptors.get(Before.class), request, response);
			Object result = invoke(methodAction, request, response);
			interceptorManager.intercept(methodAction, interceptors.get(After.class), request, response);
			return getModelAndView(result, request.method, methodAction.method());
		} catch (Throwable e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			Interceptor interceptor = interceptorManager.findProperInterceptor(interceptors.get(Catch.class), cause);

			if (interceptor == null) {
				throw e;
			}

			Object result = interceptorManager.intercept(interceptor, request, response, cause);
			Catch annotation = interceptor.method().getAnnotation(Catch.class);
			return getModelAndView(result, annotation.view());
		} finally {
			interceptorManager.intercept(methodAction, interceptors.get(Finally.class), request, response);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.binderManager = BeanUtils.getBean(settings, applicationContext, BinderManager.class);
		this.viewResolver = BeanUtils.getBean(settings, applicationContext, ViewResolver.class);
		this.interceptorManager = BeanUtils.getBean(settings, applicationContext, InterceptorManager.class);
	}
}
