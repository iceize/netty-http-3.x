package ice.http.server.remote.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Namespace {
	String value();

	boolean exposeAll() default false;
}
