package ice.http.server.action;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.annotations.Catch;
import ice.http.server.annotations.With;
import ice.http.server.binder.BindUtils;
import ice.http.server.utils.BeanUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class InterceptorManager implements ApplicationContextAware {
	private ApplicationContext applicationContext;
	private final ConcurrentMap<Class<?>, Object> interceptorInstances = Maps.newConcurrentMap();

	private Map<Integer, Set<Interceptor>> getInterceptors0(final Object bean, final Class<? extends Annotation> annotationClass) {
		final Map<Integer, Set<Interceptor>> interceptorMap = Maps.newTreeMap();

		ReflectionUtils.doWithMethods(bean.getClass(), new MethodCallback() {
			private Set<Class<? extends Annotation>> getAnnotations(Class<? extends Annotation>[] annotations) {
				if (ArrayUtils.isEmpty(annotations)) {
					return null;
				}

				Set<Class<? extends Annotation>> set = Sets.newLinkedHashSetWithExpectedSize(annotations.length);
				Collections.addAll(set, annotations);
				return set;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
				Annotation annotation = method.getAnnotation(annotationClass);
				Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(annotation);

				int priority = MapUtils.getIntValue(annotationAttributes, "priority", 0);
				Set<Class<? extends Annotation>> only = getAnnotations((Class<? extends Annotation>[]) annotationAttributes.get("only"));
				Set<Class<? extends Annotation>> unless = getAnnotations((Class<? extends Annotation>[]) annotationAttributes.get("unless"));

				Set<Interceptor> interceptors = interceptorMap.get(priority);

				if (interceptors == null) {
					interceptors = Sets.newLinkedHashSet();
				}

				interceptors.add(new Interceptor(bean, method, only, unless));
				interceptorMap.put(priority, interceptors);
			}
		}, method -> method.isAnnotationPresent(annotationClass));

		return interceptorMap;
	}

	private Object createOrGetInterceptor(Class<?> interceptorClass) {
		if (!interceptorInstances.containsKey(interceptorClass)) {
			try {
				Object interceptorInstance = applicationContext.getBean(interceptorClass);

				if (interceptorClass != null) {
					interceptorInstances.putIfAbsent(interceptorClass, interceptorInstance);
				}
			} catch (BeansException ignored) {
			}
		}

		if (!interceptorInstances.containsKey(interceptorClass)) {
			try {
				Object interceptorInstance = BeanUtils.createBean(interceptorClass, null, applicationContext);

				if (interceptorInstance != null) {
					interceptorInstances.putIfAbsent(interceptorClass, interceptorInstance);
				}
			} catch (Exception ignored) {
			}
		}

		return interceptorInstances.get(interceptorClass);
	}

	private Set<Interceptor> getInterceptors(Object bean, Class<? extends Annotation> annotationClass) {
		Map<Integer, Set<Interceptor>> sortedInterceptorMap = Maps.newTreeMap();
		sortedInterceptorMap.putAll(getInterceptors0(bean, annotationClass));

		With with = bean.getClass().getAnnotation(With.class);

		if (with != null) {
			for (Class<?> interceptorClass : with.value()) {
				Object interceptor = createOrGetInterceptor(interceptorClass);

				if (interceptor == null) {
					continue;
				}

				Map<Integer, Set<Interceptor>> interceptorMap = getInterceptors0(interceptor, annotationClass);

				for (Entry<Integer, Set<Interceptor>> entry : interceptorMap.entrySet()) {
					Set<Interceptor> interceptors = sortedInterceptorMap.get(entry.getKey());

					if (interceptors == null) {
						interceptors = Sets.newLinkedHashSet();
					}

					interceptors.addAll(entry.getValue());
					sortedInterceptorMap.put(entry.getKey(), interceptors);
				}
			}
		}

		Set<Interceptor> interceptors = Sets.newLinkedHashSet();

		for (Entry<Integer, Set<Interceptor>> entry : sortedInterceptorMap.entrySet()) {
			interceptors.addAll(entry.getValue());
		}

		return interceptors;
	}

	public void addInterceptors(MethodAction methodAction) {
		Object bean = methodAction.bean();

		Map<Class<? extends Annotation>, Set<Interceptor>> interceptors = Maps.newHashMap();

		for (Class<? extends Annotation> interceptorAnnotation : Interceptor.ANNOTATIONS) {
			interceptors.put(interceptorAnnotation, getInterceptors(bean, interceptorAnnotation));
		}

		methodAction.interceptors(interceptors);
	}

	public Object intercept(Interceptor interceptor, Request request, Response response, Throwable cause) {
		Class<?>[] parameterTypes = interceptor.method().getParameterTypes();

		if (parameterTypes == null || parameterTypes.length == 0) {
			return ReflectionUtils.invokeMethod(interceptor.method(), interceptor.bean());
		}

		List<Object> args = Lists.newArrayList();

		for (Class<?> parameterType : parameterTypes) {
			args.add(BindUtils.defaultValue(parameterType, request, response, cause));
		}

		return ReflectionUtils.invokeMethod(interceptor.method(), interceptor.bean(), args.toArray(new Object[args.size()]));
	}

	private boolean interceptMatched(MethodAction methodAction, Interceptor interceptor) {
		Set<Class<? extends Annotation>> annotations = methodAction.annotations();

		if (interceptor.only() != null) {
			return annotations != null && !Sets.intersection(interceptor.only(), annotations).isEmpty();
		}

		if (interceptor.unless() != null) {
			return annotations == null || Sets.intersection(interceptor.unless(), annotations).isEmpty();
		}

		return true;
	}

	public void intercept(MethodAction methodAction, Set<Interceptor> interceptors, Request request, Response response) {
		if (CollectionUtils.isEmpty(interceptors)) {
			return;
		}

		for (Interceptor interceptor : interceptors) {
			if (interceptMatched(methodAction, interceptor)) {
				intercept(interceptor, request, response, null);
			}
		}
	}

	public Interceptor findProperInterceptor(Set<Interceptor> interceptors, Throwable cause) {
		if (CollectionUtils.isEmpty(interceptors)) {
			return null;
		}

		for (Interceptor interceptor : interceptors) {
			Catch annotation = interceptor.method().getAnnotation(Catch.class);

			for (Class<?> clazz : annotation.value()) {
				if (clazz.isAssignableFrom(cause.getClass())) {
					return interceptor;
				}
			}
		}

		return null;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
