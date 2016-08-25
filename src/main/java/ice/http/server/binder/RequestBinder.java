package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

@Bind(Request.class)
public class RequestBinder implements Binder {
	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		return request;
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return request;
	}
}
