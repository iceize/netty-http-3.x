package ice.http.server.view;

import ice.http.server.Request;
import ice.http.server.Response;

public interface View {
	void apply(Object result, Request request, Response response);
}
