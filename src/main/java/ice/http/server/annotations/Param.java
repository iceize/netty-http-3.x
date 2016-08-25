package ice.http.server.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
	String DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n";

	String value() default "";

	String defaultValue() default DEFAULT_NONE;

	String description() default "";

	// {@link RequestBody}에서만 사용
	Class<?> to() default Class.class;
}
