package ice.http.server.binder;

import java.net.URLDecoder;

@Bind(String.class)
public class StringBinder extends AbstractBinder {
	static Object DEFAULT_VALUE = null;

	@SuppressWarnings("deprecation")
	@Override
	protected Object bind(Class<?> clazz, Object value) {
		return value.getClass() == String.class ? URLDecoder.decode((String) value) : URLDecoder.decode(value.toString());
	}

	@Override
	public Object defaultValue(Class<?> clazz) {
		return DEFAULT_VALUE;
	}
}
