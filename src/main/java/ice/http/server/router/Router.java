package ice.http.server.router;

import ice.http.server.Request;
import ice.http.server.action.Action;

public interface Router {
	Action route(Request request);
}
