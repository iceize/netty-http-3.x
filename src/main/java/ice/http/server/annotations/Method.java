package ice.http.server.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Method {
	HttpMethod value();

	enum HttpMethod {
		GET, POST, HEAD, OPTIONS, PUT, PATCH, DELETE, TRACE, WS
	}
}
