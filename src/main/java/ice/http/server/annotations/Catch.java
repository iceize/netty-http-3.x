package ice.http.server.annotations;

import ice.http.server.view.View;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Catch {
	int priority() default 0;

	Class<?>[] value();

	Class<? extends View> view() default View.class;
}
