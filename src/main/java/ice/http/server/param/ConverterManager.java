package ice.http.server.param;

import com.google.common.collect.Maps;
import ice.http.server.Parameter;
import ice.http.server.Request;
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

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ConverterManager implements SettingsAware, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private ApplicationContext applicationContext;
	private final Map<Class<? extends ConvertWith>, Converter> converters = Maps.newHashMap();
	private final Logger logger = LoggerFactory.getLogger(ConverterManager.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public Object convert(Request request, Parameter parameter, String value) {
		Annotation[] annotations = parameter.annotations;

		if (annotations == null || annotations.length == 0) {
			return null;
		}

		for (Annotation annotation : annotations) {
			Converter converter = converters.get(annotation.annotationType());

			if (converter != null) {
				return converter.convert(request, parameter, value);
			}
		}

		return null;
	}

	private void registerConverter(String basePackage) {
		Set<Class<? extends ConvertWith>> annotationClasses = ClassPathScanner.scan(basePackage, new ClassPathScanner.AnnotationFilter(ConvertWith.class));

		for (Class<? extends ConvertWith> annotationClass : annotationClasses) {
			ConvertWith convertWith = annotationClass.getAnnotation(ConvertWith.class);

			try {
				Converter converter = BeanUtils.createBean(convertWith.value(), settings, applicationContext);

				if (converter != null) {
					converters.put(annotationClass, converter);
				}
			} catch (Exception ignored) {
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<Class<? extends ConvertWith>, Converter> entry : converters.entrySet()) {
				logger.debug("[converter] @{} => {}", entry.getKey().getSimpleName(), entry.getValue().getClass());
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		registerConverter(this.getClass().getPackage().getName());

		if (settings.getExtensions() != null) {
			for (String basePackage : StringUtils.split(settings.getExtensions(), ",")) {
				registerConverter(basePackage);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
