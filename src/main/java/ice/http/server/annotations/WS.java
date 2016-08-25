package ice.http.server.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Method(Method.HttpMethod.WS)
public @interface WS {
	String[] value();

	long delay() default -1L;

	Event event() default Event.periodic;

	String summary() default "";

	String description() default "";

	enum Event {
		periodic,
		request
	}
}
