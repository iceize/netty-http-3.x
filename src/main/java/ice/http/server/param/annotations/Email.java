package ice.http.server.param.annotations;

import ice.http.server.param.CheckWith;
import ice.http.server.param.EmailValidator;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@CheckWith(EmailValidator.class)
public @interface Email {
	String value() default "";

	String message() default "";
}
