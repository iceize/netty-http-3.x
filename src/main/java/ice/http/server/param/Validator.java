package ice.http.server.param;

import java.util.Map;

public interface Validator {
	boolean isSatisfied(Object object, Map<String, Object> attributes);
}
