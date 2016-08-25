package ice.http.server;

import ice.http.server.view.View;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Configuration
public @interface Http {
	String port();

	String threadCount() default "0";

	Class<? extends View> view() default View.class;
}
