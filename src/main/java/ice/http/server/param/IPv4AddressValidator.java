package ice.http.server.param;

import java.util.Map;

public class IPv4AddressValidator implements Validator {
	@Override
	public boolean isSatisfied(Object object, Map<String, Object> attributes) {
		if (object == null) {
			return false;
		}

		if (!(object instanceof String)) {
			return false;
		}

		String value = (String) object;

		try {
			String[] parts = value.split("[.]");

			if (parts.length != 4) {
				return false;
			}

			for (String part : parts) {
				int p = Integer.valueOf(part);

				if (p < 0 || p > 255) {
					return false;
				}
			}

			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
