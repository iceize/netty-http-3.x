package ice.http.server.remote.client;

import com.google.common.collect.Maps;
import ice.http.server.remote.annotations.Name;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class RemoteClientBeanFactoryPostProcessor implements InitializingBean, BeanFactoryPostProcessor {
	private String serverAddress;
	private boolean useParameterNames;
	private HttpClient httpClient;
	private String basePackage;
	private Map<String, Class<?>> interfaces;
	private final Logger logger = LoggerFactory.getLogger(RemoteClientBeanFactoryPostProcessor.class);

	public void setServerAddress(String serverAddress) {
		this.serverAddress = serverAddress;
	}

	public void setUseParameterNames(boolean useParameterNames) {
		this.useParameterNames = useParameterNames;
	}

	public void setHttpClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public void setBasePackage(String basePackage) {
		this.basePackage = basePackage;
	}

	public void setInterfaces(Map<String, Class<?>> interfaces) {
		this.interfaces = interfaces;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) configurableListableBeanFactory;

		if (basePackage != null) {
			ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(false);
			componentProvider.addIncludeFilter(new AnnotationTypeFilter(Name.class));
			Set<BeanDefinition> beanDefinitions = componentProvider.findCandidateComponents(basePackage);

			if (interfaces == null) {
				interfaces = Maps.newLinkedHashMap();
			}

			for (BeanDefinition beanDefinition : beanDefinitions) {
				try {
					Class<?> clazz = ClassUtils.forName(beanDefinition.getBeanClassName(), ClassUtils.getDefaultClassLoader());
					Name name = clazz.getAnnotation(Name.class);

					if (name == null || name.value() == null || "".equals(name.value())) {
						continue;
					}

					interfaces.put(name.value(), clazz);
				} catch (Exception | LinkageError ignored) {
				}
			}
		}

		for (Entry<String, Class<?>> entry : interfaces.entrySet()) {
			ProxyFactory proxyFactory = new ProxyFactory();
			Class<?> clazz = entry.getValue();

			if (clazz.isInterface()) {
				proxyFactory.addInterface(clazz);
			} else {
				proxyFactory.setTargetClass(clazz);
			}

			proxyFactory.addAdvice(new RemoteClientMethodInterceptor(clazz, httpClient, entry.getKey(), serverAddress, useParameterNames));
			Object proxy = proxyFactory.getProxy();

			beanFactory.registerSingleton(ClassUtils.getShortNameAsProperty(clazz) + "@" + proxy.hashCode(), proxy);
			logger.debug("{} is registered...", ClassUtils.getShortNameAsProperty(clazz));
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(serverAddress, "serverAddress should be configured");

		if (basePackage == null && interfaces == null) {
			throw new IllegalArgumentException("basePackage or interfaces should be configured");
		}

		if (httpClient == null) {
			httpClient = HttpClientBuilder.create().build();
		}
	}
}
