package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

public abstract class AbstractBinder implements Binder {
	protected abstract Object bind(Class<?> clazz, Object value);

	protected abstract Object defaultValue(Class<?> clazz);

	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		Object defaultValue = defaultValue(request, response, parameter);

		try {
			return value == null ? defaultValue : bind(parameter.bindClass, value);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		try {
			if (parameter.defaultValue != null) {
				return bind(parameter.bindClass, parameter.defaultValue);
			}
		} catch (Exception ignored) {
		}

		return defaultValue(parameter.bindClass);
	}
}
