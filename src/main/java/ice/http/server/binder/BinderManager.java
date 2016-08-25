package ice.http.server.binder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.*;
import ice.http.server.param.ConverterManager;
import ice.http.server.param.ValidationException;
import ice.http.server.param.ValidatorManager;
import ice.http.server.utils.BeanUtils;
import ice.http.server.utils.ClassPathScanner;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BinderManager implements SettingsAware, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private ApplicationContext applicationContext;
	private ConverterManager converterManager;
	private ValidatorManager validatorManager;
	private final Map<Class<?>, Binder> binders = Maps.newHashMap();
	private final Logger logger = LoggerFactory.getLogger(BinderManager.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public Object bind(Request request, Response response, Parameter parameter, Map<String, List<String>> requestParams) {
		List<String> paramValues = requestParams.get(parameter.paramName);
		List<Object> convertedValues = null;

		if (paramValues != null) {
			convertedValues = Lists.newArrayListWithExpectedSize(paramValues.size());

			for (String paramValue : paramValues) {
				Object convertedValue = converterManager.convert(request, parameter, paramValue);
				convertedValues.add(convertedValue == null ? paramValue : convertedValue);
			}
		}

		Object bound = bind0(request, response, parameter, convertedValues);

		boolean valid = true;

		try {
			Annotation annotation = validatorManager.validate(bound, parameter);

			if (annotation != null) {
				valid = false;
			}
		} catch (Exception e) {
			throw new ValidationException(parameter.paramName + " is not valid.", e);
		}

		if (!valid) {
			throw new ValidationException(parameter.paramName + " is not valid.");
		}

		return bound;
	}

	private Object bind0(Request request, Response response, Parameter parameter, List<Object> values) {
		if (CollectionUtils.isEmpty(values)) {
			return bind1(request, response, parameter, null);
		}

		Class<?> clazz = parameter.clazz;

		if (clazz.isArray()) {
			Object array = Array.newInstance(parameter.bindClass, values.size());

			for (int i = 0; i < values.size(); i++) {
				Array.set(array, i, bind1(request, response, parameter, values.get(i)));
			}

			return array;
		} else {
			return bind1(request, response, parameter, values.iterator().next());
		}
	}

	private Object bind1(Request request, Response response, Parameter parameter, Object value) {
		Binder binder = binders.get(parameter.bindClass);

		if (binder == null) {
			for (Entry<Class<?>, Binder> entry : binders.entrySet()) {
				if (entry.getKey().isAssignableFrom(parameter.bindClass)) {
					binder = entry.getValue();
					break;
				}
			}
		}

		if (binder == null) {
			return value;
		}

		return binder.bind(request, response, parameter, value);
	}

	private void registerBinder(String basePackage) {
		Set<Class<? extends Binder>> binderClasses = ClassPathScanner.scan(basePackage, new ClassPathScanner.AnnotationFilter(Bind.class));

		for (Class<? extends Binder> binderClass : binderClasses) {
			try {
				Bind bind = binderClass.getAnnotation(Bind.class);
				Binder binder = BeanUtils.createBean(binderClass, settings, applicationContext);

				if (binder != null) {
					for (Class<?> type : bind.value()) {
						binders.put(type, binder);
					}
				}
			} catch (Exception e) {
				logger.debug(e.getMessage(), e);
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<Class<?>, Binder> entry : binders.entrySet()) {
				logger.debug("[binder] {} => {}", entry.getKey().getSimpleName(), entry.getValue().getClass());
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		registerBinder(Binder.class.getPackage().getName());

		if (settings.getExtensions() != null) {
			for (String basePackage : StringUtils.split(settings.getExtensions(), ",")) {
				registerBinder(basePackage);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		this.converterManager = BeanUtils.getBean(settings, applicationContext, ConverterManager.class);
		this.validatorManager = BeanUtils.getBean(settings, applicationContext, ValidatorManager.class);
	}
}
