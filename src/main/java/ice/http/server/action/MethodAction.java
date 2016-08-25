package ice.http.server.action;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import ice.http.server.Parameter;
import ice.http.server.annotations.Method.HttpMethod;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MethodAction implements Action {
	private final Object bean;
	private final Method method;
	private final HttpMethod httpMethod;
	private final String path;
	private final Map<String, Parameter> parameters;
	private final Set<Class<? extends Annotation>> annotations;
	private Map<Class<? extends Annotation>, Set<Interceptor>> interceptors = Collections.emptyMap();

	public MethodAction(Object bean, Method method, HttpMethod httpMethod, String path, Map<String, Parameter> parameters) {
		this.bean = bean;
		this.method = method;
		this.httpMethod = httpMethod;
		this.path = path;
		this.parameters = parameters;

		Annotation[] annotations = method.getAnnotations();

		if (ArrayUtils.isEmpty(annotations)) {
			this.annotations = null;
		} else {
			this.annotations = Sets.newLinkedHashSetWithExpectedSize(annotations.length);

			for (Annotation annotation : annotations) {
				this.annotations.add(annotation.annotationType());
			}
		}
	}

	public Object bean() {
		return bean;
	}

	public Method method() {
		return method;
	}

	public HttpMethod httpMethod() {
		return httpMethod;
	}

	public String path() {
		return path;
	}

	public Map<String, Parameter> parameters() {
		return parameters;
	}

	public Set<Class<? extends Annotation>> annotations() {
		return annotations;
	}

	public Map<Class<? extends Annotation>, Set<Interceptor>> interceptors() {
		return interceptors;
	}

	public void interceptors(Map<Class<? extends Annotation>, Set<Interceptor>> interceptors) {
		this.interceptors = interceptors;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(bean.getClass().getSimpleName()).append("#").append(method.getName());
		sb.append("(");

		if (parameters != null) {
			List<String> params = Lists.newArrayListWithExpectedSize(parameters.size());

			for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
				params.add(entry.getValue().clazz.getSimpleName() + " " + entry.getKey());
			}

			sb.append(StringUtils.join(params, ", "));
		}

		sb.append(")");

		return sb.toString();
	}
}
