package ice.http.server.utils;

import com.google.common.collect.Maps;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentMap;

public final class BeanUtils {
	private static final ConcurrentMap<String, Object> beanMap = Maps.newConcurrentMap();

	private BeanUtils() {
		throw new UnsupportedOperationException();
	}

	public static String beanName(Settings settings, Class<?> clazz) {
		return clazz.getSimpleName() + "@" + settings.getName();
	}

	@SuppressWarnings("unchecked")
	public static <T> T getBean(Settings settings, ApplicationContext applicationContext, Class<T> clazz) {
		String beanName = beanName(settings, clazz);

		if (beanMap.containsKey(beanName)) {
			return (T) beanMap.get(beanName);
		}

		try {
			T bean = applicationContext.getBean(beanName, clazz);
			beanMap.putIfAbsent(beanName, bean);
			return bean;
		} catch (BeansException e) {
			return null;
		}
	}

	public static <T> T createBean(Class<T> clazz, Settings settings, ApplicationContext applicationContext) throws Exception {
		if (clazz == null || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
			return null;
		}

		T bean = clazz.newInstance();

		if (bean instanceof SettingsAware) {
			((SettingsAware) bean).setSettings(settings);
		}

		if (bean instanceof ApplicationContextAware) {
			((ApplicationContextAware) bean).setApplicationContext(applicationContext);
		}

		if (bean instanceof InitializingBean) {
			((InitializingBean) bean).afterPropertiesSet();
		}

		return bean;
	}
}
