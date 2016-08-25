package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

@Bind(Throwable.class)
public class ThrowableBinder implements Binder {
	static Object DEFAULT_VALUE = null;

	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		return request.exception.getClass().isAssignableFrom(parameter.clazz) ? request.exception : defaultValue(request, response, parameter);
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return DEFAULT_VALUE;
	}
}
