package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;

import java.util.Map;

@Bind(Map.class)
public class ParamBinder implements Binder {
	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		return request.param();
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return request.param();
	}
}
