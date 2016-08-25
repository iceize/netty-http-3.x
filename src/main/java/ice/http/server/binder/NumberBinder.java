package ice.http.server.binder;

@Bind({byte.class, short.class, int.class, long.class, float.class, double.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class})
public class NumberBinder extends AbstractBinder {
	static Object DEFAULT_VALUE = 0;

	@Override
	protected Object bind(Class<?> clazz, Object value) {
		Class<?> valueClass = value.getClass();

		if (byte.class == clazz || Byte.class == clazz) {
			return valueClass == String.class ? Byte.valueOf((String) value) : value;
		}

		if (short.class == clazz || Short.class == clazz) {
			return valueClass == String.class ? Short.valueOf((String) value) : value;
		}

		if (int.class == clazz || Integer.class == clazz) {
			return valueClass == String.class ? Integer.valueOf((String) value) : value;
		}

		if (long.class == clazz || Long.class == clazz) {
			return valueClass == String.class ? Long.valueOf((String) value) : value;
		}

		if (float.class == clazz || Float.class == clazz) {
			return valueClass == String.class ? Float.valueOf((String) value) : value;
		}

		if (double.class == clazz || Double.class == clazz) {
			return valueClass == String.class ? Double.valueOf((String) value) : value;
		}

		return null;
	}

	@Override
	public Object defaultValue(Class<?> clazz) {
		return clazz.isPrimitive() ? DEFAULT_VALUE : null;
	}
}
