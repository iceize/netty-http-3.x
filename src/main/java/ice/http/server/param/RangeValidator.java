package ice.http.server.param;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Map;

public class RangeValidator implements Validator {
	private Number getNumber(Map<?, ?> map, Object key) {
		if (map == null) {
			return null;
		}

		Object answer = map.get(key);

		if (answer != null) {
			if (answer instanceof Number) {
				return (Number) answer;
			} else if (answer instanceof String) {
				try {
					String text = (String) answer;
					return NumberFormat.getInstance().parse(text);
				} catch (ParseException ignored) {
				}
			}
		}

		return null;
	}

	private Double getDouble(Map<?, ?> map, Object key) {
		Number answer = getNumber(map, key);

		if (answer == null) {
			return null;
		} else if (answer instanceof Double) {
			return (Double) answer;
		}

		return answer.doubleValue();
	}

	private double getDoubleValue(Map<?, ?> map, Object key) {
		Double doubleObject = getDouble(map, key);
		return doubleObject == null ? 0d : doubleObject;
	}

	@Override
	public boolean isSatisfied(Object object, Map<String, Object> attributes) {
		if (object == null) {
			return true;
		}

		double min = getDoubleValue(attributes, "min");
		double max = getDoubleValue(attributes, "max");

		if (object instanceof String) {
			try {
				double v = Double.parseDouble(object.toString());
				return v >= min && v <= max;
			} catch (Exception e) {
				return false;
			}
		}

		if (object instanceof Number) {
			try {
				return ((Number) object).doubleValue() >= min && ((Number) object).doubleValue() <= max;
			} catch (Exception e) {
				return false;
			}
		}

		return false;
	}
}
