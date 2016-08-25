package ice.http.server.binder;

@Bind({char.class, Character.class})
public class CharacterBinder extends AbstractBinder {
	static Object DEFAULT_VALUE = '\u0000';

	@Override
	protected Object bind(Class<?> clazz, Object value) {
		Class<?> valueClass = value.getClass();

		if (char.class == valueClass || Character.class == valueClass) {
			return value;
		}

		return ((String) value).charAt(0);
	}

	@Override
	public Object defaultValue(Class<?> clazz) {
		return clazz.isPrimitive() ? DEFAULT_VALUE : null;
	}
}
