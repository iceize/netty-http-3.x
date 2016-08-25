package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

@Bind(Response.class)
public class ResponseBinder implements Binder {
	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		return response;
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return response;
	}
}
