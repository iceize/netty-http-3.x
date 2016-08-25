package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

public interface Binder {
	Object bind(Request request, Response response, Parameter parameter, Object value);

	Object defaultValue(Request request, Response response, Parameter parameter);
}
