package ice.http.server.binder;

import ice.http.server.Request;
import ice.http.server.Response;

public final class BindUtils {
	private BindUtils() {
		throw new UnsupportedOperationException();
	}

	public static Object defaultValue(Class<?> clazz, Request request, Response response, Throwable cause) {
		if (null == clazz) {
			return null;
		}

		if (Request.class == clazz) {
			return request;
		}

		if (Response.class == clazz) {
			return response;
		}

		if (Request.RequestSession.class.isAssignableFrom(clazz)) {
			return request.requestSession;
		}

		if (Request.RequestBody.class == clazz) {
			return request.body;
		}

		if (Throwable.class.isAssignableFrom(clazz)) {
			return cause;
		}

		if (boolean.class == clazz) {
			return BooleanBinder.DEFAULT_VALUE;
		}

		if (char.class == clazz) {
			return CharacterBinder.DEFAULT_VALUE;
		}

		if (clazz.isPrimitive()) {
			return NumberBinder.DEFAULT_VALUE;
		}

		return null;
	}
}
