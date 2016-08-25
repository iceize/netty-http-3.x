package ice.http.server;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class Parameter {
	public final Type type;
	public final Class<?> clazz;
	public final Class<?> bindClass;
	public final String name;
	public final String paramName;
	public final boolean required;
	public final Annotation[] annotations;
	public final String defaultValue;

	public Parameter(Type type, Class<?> clazz, String name, String paramName, boolean required, Annotation[] annotations, String defaultValue, Class<?> convertTo) {
		this.type = type;
		this.clazz = clazz;
		this.bindClass = convertTo == null ? (clazz.isArray() ? clazz.getComponentType() : clazz) : convertTo;
		this.name = name;
		this.paramName = paramName;
		this.required = required;
		this.annotations = annotations;
		this.defaultValue = defaultValue;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
