package ice.http.server.param.annotations;

import ice.http.server.param.CheckWith;
import ice.http.server.param.IPv4AddressValidator;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@CheckWith(IPv4AddressValidator.class)
public @interface IPv4Address {
	String value() default "";

	String message() default "";
}
