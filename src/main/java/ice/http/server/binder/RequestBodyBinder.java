package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

@Bind(Request.RequestBody.class)
public class RequestBodyBinder implements Binder {
	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		return request.body;
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return request.body;
	}
}
