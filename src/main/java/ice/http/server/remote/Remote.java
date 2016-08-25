package ice.http.server.remote;

import ice.http.server.view.View;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Configuration
public @interface Remote {
	String port();

	String threadCount() default "0";

	Class<? extends View> view() default View.class;
}
