package ice.http.server.view;

import com.google.common.collect.Maps;
import ice.http.server.Context;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
import ice.http.server.annotations.Method.HttpMethod;
import ice.http.server.utils.BeanUtils;
import ice.http.server.utils.ClassPathScanner;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class ViewResolver implements SettingsAware, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private ApplicationContext applicationContext;
	private View defaultView;
	private final Map<Class<? extends View>, View> views = Maps.newHashMap();
	private final ConcurrentMap<MethodKey, View> methodViews = Maps.newConcurrentMap();
	private final Logger logger = LoggerFactory.getLogger(ViewResolver.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public View resolve(Class<? extends View> view) {
		if (view == null || view == View.class || !views.containsKey(view)) {
			return defaultView;
		}

		return views.get(view);
	}

	@SuppressWarnings("unchecked")
	public View resolve(HttpMethod httpMethod, Method method) {
		View view = methodViews.get(new MethodKey(httpMethod, method));

		if (view != null) {
			return view;
		}

		Annotation annotation = null;

		for (Class<? extends Annotation> annotationClass : Context.getMvcAnnotations().keySet()) {
			Annotation anno = method.getAnnotation(annotationClass);

			if (anno != null) {
				annotation = anno;
				break;
			}
		}

		if (annotation == null) {
			return null;
		}

		try {
			Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(annotation);
			View resolved = resolve((Class<? extends View>) annotationAttributes.get("view"));
			methodViews.putIfAbsent(new MethodKey(httpMethod, method), resolved);
			return resolved;
		} catch (Exception ignored) {
		}

		return null;
	}

	private void registerView(String basePackage) {
		Set<Class<? extends View>> viewClasses = ClassPathScanner.scan(basePackage, new ClassPathScanner.AssignableFilter(View.class));

		for (Class<? extends View> viewClass : viewClasses) {
			try {
				if (!views.containsKey(viewClass)) {
					View view = BeanUtils.createBean(viewClass, settings, applicationContext);

					if (view != null) {
						views.put(viewClass, view);
					}
				}
			} catch (Exception e) {
				logger.debug(e.getMessage(), e);
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<Class<? extends View>, View> entry : views.entrySet()) {
				logger.debug("[view] {} => {}", entry.getKey().getSimpleName(), entry.getValue().getClass());
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		try {
			defaultView = BeanUtils.createBean(settings.getDefaultView(), settings, applicationContext);
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		}

		registerView(this.getClass().getPackage().getName());

		if (settings.getExtensions() != null) {
			for (String basePackage : StringUtils.split(settings.getExtensions(), ",")) {
				registerView(basePackage);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	static class MethodKey {
		final HttpMethod httpMethod;
		final Method method;

		MethodKey(HttpMethod httpMethod, Method method) {
			this.httpMethod = httpMethod;
			this.method = method;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((httpMethod == null) ? 0 : httpMethod.hashCode());
			result = prime * result + ((method == null) ? 0 : method.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			if (obj == null) {
				return false;
			}

			if (getClass() != obj.getClass()) {
				return false;
			}

			MethodKey other = (MethodKey) obj;

			if (httpMethod != other.httpMethod) {
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
	}
}
