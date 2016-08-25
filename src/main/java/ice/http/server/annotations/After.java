package ice.http.server.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface After {
	int priority() default 0;

	Class<? extends Annotation>[] only() default {};

	Class<? extends Annotation>[] unless() default {};
}
