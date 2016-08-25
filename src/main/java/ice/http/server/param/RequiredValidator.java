package ice.http.server.param;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

public class RequiredValidator implements Validator {
	@Override
	public boolean isSatisfied(Object object, Map<String, Object> attributes) {
		if (object == null) {
			return false;
		}

		if (object instanceof String) {
			return object.toString().trim().length() > 0;
		}

		if (object instanceof Collection<?>) {
			return ((Collection<?>) object).size() > 0;
		}

		if (object.getClass().isArray()) {
			try {
				return Array.getLength(object) > 0;
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		}

		return true;
	}
}
