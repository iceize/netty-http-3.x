package ice.http.server.param.annotations;

import ice.http.server.param.CheckWith;
import ice.http.server.param.RequiredValidator;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@CheckWith(RequiredValidator.class)
public @interface Required {
	String value() default "";

	String message() default "";
}
