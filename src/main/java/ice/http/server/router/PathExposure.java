package ice.http.server.router;

import ice.http.server.action.Action;
import ice.http.server.annotations.Method;

import java.util.Map;

public interface PathExposure {
	Map<Method.HttpMethod, Map<String, Action>> getPaths();
}
