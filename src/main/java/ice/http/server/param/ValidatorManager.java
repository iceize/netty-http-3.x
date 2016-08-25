package ice.http.server.param;

import com.google.common.collect.Maps;
import ice.http.server.Parameter;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ValidatorManager implements SettingsAware, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private ApplicationContext applicationContext;
	private final Map<Class<? extends CheckWith>, Validator> validators = Maps.newHashMap();
	private final Logger logger = LoggerFactory.getLogger(ValidatorManager.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public Annotation validate(Object value, Parameter parameter) {
		Annotation[] annotations = parameter.annotations;

		if (annotations == null || annotations.length == 0) {
			return null;
		}

		for (Annotation annotation : annotations) {
			Validator validator = validators.get(annotation.annotationType());

			if (validator != null) {
				if (!validator.isSatisfied(value, AnnotationUtils.getAnnotationAttributes(annotation))) {
					return annotation;
				}
			}
		}

		return null;
	}

	private void registerValidator(String basePackage) {
		Set<Class<? extends CheckWith>> annotationClasses = ClassPathScanner.scan(basePackage, new ClassPathScanner.AnnotationFilter(CheckWith.class));

		for (Class<? extends CheckWith> annotationClass : annotationClasses) {
			CheckWith checkWith = annotationClass.getAnnotation(CheckWith.class);

			try {
				Validator validator = BeanUtils.createBean(checkWith.value(), settings, applicationContext);

				if (validator != null) {
					validators.put(annotationClass, validator);
				}
			} catch (Exception ignored) {
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<Class<? extends CheckWith>, Validator> entry : validators.entrySet()) {
				logger.debug("[validator] @{} => {}", entry.getKey().getSimpleName(), entry.getValue().getClass());
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		registerValidator(this.getClass().getPackage().getName());

		if (settings.getExtensions() != null) {
			for (String basePackage : StringUtils.split(settings.getExtensions(), ",")) {
				registerValidator(basePackage);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
