package ice.http.server.param.annotations;

import ice.http.server.param.CheckWith;
import ice.http.server.param.RangeValidator;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@CheckWith(RangeValidator.class)
public @interface Range {
	double min() default Double.MIN_VALUE;

	double max() default Double.MAX_VALUE;

	String message() default "";
}
