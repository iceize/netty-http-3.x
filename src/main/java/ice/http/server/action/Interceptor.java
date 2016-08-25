package ice.http.server.action;

import ice.http.server.annotations.After;
import ice.http.server.annotations.Before;
import ice.http.server.annotations.Catch;
import ice.http.server.annotations.Finally;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Interceptor {
	private final Object bean;
	private final Method method;
	private final Set<Class<? extends Annotation>> only;
	private final Set<Class<? extends Annotation>> unless;
	static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Before.class, After.class, Catch.class, Finally.class);

	public Interceptor(Object bean, Method method, Set<Class<? extends Annotation>> only, Set<Class<? extends Annotation>> unless) {
		this.bean = bean;
		this.method = method;
		this.only = only;
		this.unless = unless;
	}

	public Object bean() {
		return bean;
	}

	public Method method() {
		return method;
	}

	public Set<Class<? extends Annotation>> only() {
		return only;
	}

	public Set<Class<? extends Annotation>> unless() {
		return unless;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bean == null) ? 0 : bean.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || (getClass() != obj.getClass())) {
			return false;
		}

		Interceptor other = (Interceptor) obj;

		if (bean == null) {
			if (other.bean != null) {
				return false;
			}
		} else if (!bean.equals(other.bean)) {
			return false;
		}

		if (method == null) {
			if (other.method != null) {
				return false;
			}
		} else if (!method.equals(other.method)) {
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return bean.getClass().getSimpleName() + "#" + method.toString();
	}
}
