package ice.http.server.binder;

@Bind({boolean.class, Boolean.class})
public class BooleanBinder extends AbstractBinder {
	static Object DEFAULT_VALUE = false;

	@Override
	protected Object bind(Class<?> clazz, Object value) {
		Class<?> valueClass = value.getClass();

		if (boolean.class == valueClass || Boolean.class == valueClass) {
			return value;
		}

		return Boolean.valueOf((String) value);
	}

	@Override
	public Object defaultValue(Class<?> clazz) {
		return clazz.isPrimitive() ? DEFAULT_VALUE : null;
	}
}
