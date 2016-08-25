package ice.http.server.annotations;

import ice.http.server.view.View;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Method(Method.HttpMethod.DELETE)
public @interface Delete {
	String[] value();

	Class<? extends View> view() default View.class;

	String summary() default "";

	String description() default "";
}
