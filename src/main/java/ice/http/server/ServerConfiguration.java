package ice.http.server;

import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Configuration
public @interface ServerConfiguration {
	String DEFAULT_VALUE = "application.properties";

	String[] properties() default DEFAULT_VALUE;

	String[] extensions() default {};

	Class<? extends BeanNameGenerator> beanNameGenerator() default BeanNameGenerator.class;
}
