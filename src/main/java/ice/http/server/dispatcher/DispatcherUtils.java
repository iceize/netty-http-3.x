package ice.http.server.dispatcher;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.Context;
import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.action.MethodAction;
import ice.http.server.binder.BinderManager;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public final class DispatcherUtils {
	private DispatcherUtils() {
		throw new UnsupportedOperationException();
	}

	public static Map<String, List<String>> getPathVariables(MethodAction methodAction, String requestPath) {
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

	public static Object invoke(MethodAction methodAction, Request request, Response response, BinderManager binderManager) {
		if (CollectionUtils.isEmpty(methodAction.parameters())) {
			return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean());
		}

		List<Object> args = Lists.newArrayList();
		Map<String, List<String>> requestParams = Maps.newHashMap(request.params);
		requestParams.putAll(getPathVariables(methodAction, request.path));

		for (Map.Entry<String, Parameter> entry : methodAction.parameters().entrySet()) {
			args.add(binderManager.bind(request, response, entry.getValue(), requestParams));
		}

		return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean(), args.toArray(new Object[args.size()]));
	}
}
