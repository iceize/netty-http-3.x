package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Request.RequestSession;
import ice.http.server.Response;

@Bind(RequestSession.class)
public class SessionBinder implements Binder {
	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		return request.requestSession;
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return request.requestSession;
	}
}
