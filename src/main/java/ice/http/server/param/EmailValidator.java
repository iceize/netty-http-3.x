package ice.http.server.param;

import java.util.Map;
import java.util.regex.Pattern;

public class EmailValidator implements Validator {
	private static final Pattern PATTERN = Pattern.compile("[\\w!#$%&'*+/=?^_`{|}~-]+(?:\\.[\\w!#$%&'*+/=?^_`{|}~-]+)*@(?:[\\w](?:[\\w-]*[\\w])?\\.)+[a-zA-Z0-9](?:[\\w-]*[\\w])?");

	@Override
	public boolean isSatisfied(Object object, Map<String, Object> attributes) {
		return object != null && object instanceof String && PATTERN.matcher((String) object).matches();
	}
}
