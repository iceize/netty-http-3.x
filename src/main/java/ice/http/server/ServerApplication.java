package ice.http.server;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import ice.http.server.remote.Remote;
import ice.http.server.remote.RemoteServer;
import ice.http.server.view.View;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerApplication {
	private final Class<?> mainClass;
	private final AnnotationConfigApplicationContext applicationContext;
	private Properties properties;
	private PidFileWriter pidFileWriter;
	private final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

	private ServerApplication(Class<?> clazz) {
		this.mainClass = clazz;
		this.applicationContext = new AnnotationConfigApplicationContext();

		Holder.applicationContext = this.applicationContext;
	}

	public static void run(Class<?> clazz) {
		run(clazz, null);
	}

	public static void run(Class<?> clazz, String defaultProfile) {
		run(clazz, defaultProfile, true);
	}

	public static void run(Class<?> clazz, String defaultProfile, boolean autoScan) {
		ServerApplication serverApplication = new ServerApplication(clazz);
		serverApplication.initialize(defaultProfile, autoScan);
		serverApplication.start();
	}

	private Set<Resource> getPropertiesResources(String[] properties) {
		Set<Resource> resources = Sets.newLinkedHashSet();
		String[] propertiesValues = ArrayUtils.isEmpty(properties) ? new String[]{ServerConfiguration.DEFAULT_VALUE} : properties;

		for (String propertiesValue : propertiesValues) {
			if (StringUtils.isBlank(propertiesValue)) {
				continue;
			}

			String[] splits = StringUtils.split(propertiesValue, ",");

			for (String split : splits) {
				Resource[] classpathResources = null;

				try {
					classpathResources = applicationContext.getResources("classpath*:" + StringUtils.trim(split));
				} catch (IOException ignored) {
				}

				if (classpathResources != null) {
					for (Resource resource : classpathResources) {
						if (resource.exists()) {
							resources.add(resource);
						}
					}
				}
			}
		}

		return resources;
	}

	private void initialize(String defaultProfile, boolean autoScan) {
		ServerConfiguration serverConfiguration = mainClass.getAnnotation(ServerConfiguration.class);

		if (serverConfiguration != null && serverConfiguration.beanNameGenerator() != BeanNameGenerator.class) {
			try {
				applicationContext.setBeanNameGenerator(serverConfiguration.beanNameGenerator().newInstance());
			} catch (Exception ignored) {
			}
		}

		applicationContext.register(mainClass);

		if (autoScan) {
			applicationContext.scan(mainClass.getPackage().getName());
		}

		Set<String> activeProfiles = Sets.newLinkedHashSet();
		String profileProperty = System.getProperty(AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME, defaultProfile);

		if (StringUtils.isNotBlank(profileProperty)) {
			applicationContext.getEnvironment().setActiveProfiles(StringUtils.split(profileProperty, ","));
			Collections.addAll(activeProfiles, StringUtils.split(profileProperty, ","));
			logger.warn("activeProfiles are {}", activeProfiles);
		}

		Set<Resource> resources = getPropertiesResources(serverConfiguration == null ? null : serverConfiguration.properties());

		ServerPropertiesConfigurer serverPropertiesConfigurer = new ServerPropertiesConfigurer(activeProfiles, resources);
		serverPropertiesConfigurer.setIgnoreUnresolvablePlaceholders(true);
		applicationContext.addBeanFactoryPostProcessor(serverPropertiesConfigurer);

		this.properties = ServerPropertiesConfigurer.mergeProperties(activeProfiles, resources);

		applicationContext.setEnvironment(new StandardEnvironment() {
			@Override
			protected void customizePropertySources(MutablePropertySources propertySources) {
				propertySources.addFirst(new PropertiesPropertySource("applicationProperties", properties));
				super.customizePropertySources(propertySources);
			}
		});

		for (ServerProcessor serverProcessor : ServerProcessor.values()) {
			serverProcessor.register(mainClass, applicationContext, properties);
		}

		if (defaultProfile != null && !activeProfiles.contains(defaultProfile)) {
			pidFileWriter = new PidFileWriter(new File(System.getProperty("app.home", "."), "logs/pid"));
		}

		applicationContext.refresh();
	}

	private void start() {
		CountDownLatch countDownLatch = new CountDownLatch(1);
		Map<ServerProcessor, Server> servers = Maps.newLinkedHashMap();

		try {
			for (ServerProcessor serverProcessor : ServerProcessor.values()) {
				try {
					servers.put(serverProcessor, (Server) applicationContext.getBean(serverProcessor.clazz));
					logger.info(serverProcessor.clazz.getSimpleName() + " is registered...");
				} catch (BeansException ignored) {
				}
			}

			for (Entry<ServerProcessor, Server> entry : servers.entrySet()) {
				entry.getKey().start(mainClass, entry.getValue(), properties);
				logger.info(entry.getKey().clazz.getSimpleName() + " is started...");
			}

			if (pidFileWriter != null) {
				pidFileWriter.write();
			}

			ServerSignalHandler.install("USR2", countDownLatch, servers.values().toArray(new Server[servers.size()]));
			countDownLatch.await();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new IllegalStateException(e);
		} finally {
			Map<String, ServerHook> shutdownHooks = applicationContext.getBeansOfType(ServerHook.class);

			if (!CollectionUtils.isEmpty(shutdownHooks)) {
				for (ServerHook shutdownHook : shutdownHooks.values()) {
					try {
						shutdownHook.beforeShutdown();
					} catch (Exception ignored) {
					}
				}
			}

			try {
				applicationContext.close();
			} catch (Exception ignored) {
			}

			for (Server server : servers.values()) {
				if (server != null) {
					server.stop();

					while (server.running()) {
						try {
							Thread.sleep(100L);
						} catch (InterruptedException e) { // ignore
						}
					}
				}
			}

			if (pidFileWriter != null) {
				pidFileWriter.delete();
			}
		}
	}

	private enum ServerProcessor {
		http(HttpServer.class) {
			@Override
			public void register(Class<?> mainClass, AnnotationConfigApplicationContext applicationContext, Properties properties) {
				Http http = mainClass.getAnnotation(Http.class);

				if (http == null) {
					return;
				}

				ServerConfiguration serverConfiguration = mainClass.getAnnotation(ServerConfiguration.class);

				int port = toInt(properties, Http.class, "port", http.port());
				Settings settings = new Settings(ClassUtils.getShortNameAsProperty(Http.class), port);

				if (http.view() != View.class) {
					settings.setDefaultView(http.view());
				}

				if (serverConfiguration != null && ArrayUtils.isNotEmpty(serverConfiguration.extensions())) {
					settings.setExtensions(StringUtils.join(serverConfiguration.extensions(), ","));
				}

				settings.setThreadCount(toInt(properties, Http.class, "threadCount", http.threadCount()));

				HttpServer httpServer = new HttpServer();
				httpServer.setSettings(settings);
				applicationContext.addBeanFactoryPostProcessor(httpServer);

				String beanName = ClassUtils.getShortNameAsProperty(HttpServer.class);
				BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(HttpServer.class);

				if (SettingsAware.class.isAssignableFrom(HttpServer.class)) {
					beanDefinitionBuilder.addPropertyValue("settings", settings);
				}

				applicationContext.getDefaultListableBeanFactory().registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
			}

			@Override
			public void start(Class<?> mainClass, Server server, Properties properties) {
				((HttpServer) server).bind();
			}
		},
		remote(RemoteServer.class) {
			@Override
			public void register(Class<?> mainClass, AnnotationConfigApplicationContext applicationContext, Properties properties) {
				Remote remote = mainClass.getAnnotation(Remote.class);

				if (remote == null) {
					return;
				}

				int port = toInt(properties, Remote.class, "port", remote.port());
				Settings settings = new Settings(ClassUtils.getShortNameAsProperty(Remote.class), port);

				if (remote.view() != View.class) {
					settings.setDefaultView(remote.view());
				}

				settings.setExtensions(mainClass.getPackage().getName());
				settings.setThreadCount(toInt(properties, Remote.class, "threadCount", remote.threadCount()));

				RemoteServer remoteServer = new RemoteServer();
				remoteServer.setSettings(settings);
				applicationContext.addBeanFactoryPostProcessor(remoteServer);

				String beanName = ClassUtils.getShortNameAsProperty(RemoteServer.class);
				BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(RemoteServer.class);

				if (SettingsAware.class.isAssignableFrom(RemoteServer.class)) {
					beanDefinitionBuilder.addPropertyValue("settings", settings);
				}

				applicationContext.getDefaultListableBeanFactory().registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
			}

			@Override
			public void start(Class<?> mainClass, Server server, Properties properties) {
				((RemoteServer) server).bind();
			}
		};

		public final Class<?> clazz;

		ServerProcessor(Class<?> clazz) {
			this.clazz = clazz;
		}

		public static int toInt(Properties properties, Class<? extends Annotation> annotation, String key, String value) {
			String annotationKey = "@" + annotation.getName() + " " + key;

			if (StringUtils.isEmpty(value)) {
				throw new IllegalArgumentException(annotationKey + " should be not null");
			}

			String resolved = ServerPropertiesConfigurer.resolveProperty(properties, value);

			if (!StringUtils.isNumeric(resolved)) {
				throw new IllegalArgumentException(annotationKey + " should be numeric value: value=" + resolved);
			}

			return Integer.parseInt(resolved);
		}

		public abstract void register(Class<?> mainClass, AnnotationConfigApplicationContext applicationContext, Properties properties);

		public abstract void start(Class<?> mainClass, Server server, Properties properties);
	}

	public static final class Holder {
		private static ApplicationContext applicationContext;

		public static ApplicationContext get() {
			return applicationContext;
		}
	}

	private static class PidFileWriter {
		private final File pidFile;

		PidFileWriter(File pidFile) {
			this.pidFile = pidFile;
			Assert.notNull(pidFile);
		}

		void write() {
			if ("true".equals(System.getProperty("pid.kill"))) {
				return;
			}

			if (pidFile.exists()) {
				System.err.println("pid file already exist. cannot start server.");
				System.exit(-1);
			}

			pidFile.getParentFile().mkdirs();
			OutputStream outputStream = null;

			try {
				outputStream = new FileOutputStream(pidFile);
				IOUtils.write(ManagementFactory.getRuntimeMXBean().getName().split("@")[0], outputStream, Context.DEFAULT_CHARSET);
			} catch (IOException ignored) {
			} finally {
				IOUtils.closeQuietly(outputStream);
				pidFile.deleteOnExit();
			}
		}

		void delete() {
			FileUtils.deleteQuietly(pidFile);
		}
	}

	private static class ServerPropertiesConfigurer extends PropertySourcesPlaceholderConfigurer {
		private static final Pattern PROPERTIES_PATTERN = Pattern.compile("(\\$\\{([^\\}]+)\\})");
		private final Set<String> profiles = Sets.newLinkedHashSet();
		private final Set<Resource> resources = Sets.newLinkedHashSet();

		ServerPropertiesConfigurer(Set<String> profiles, Set<Resource> resources) {
			this.profiles.addAll(profiles);
			this.resources.addAll(resources);
		}

		private static Properties mergeProperties(Set<String> profiles, Resource resource) {
			Properties properties = null;
			Properties merged = new Properties();

			try {
				properties = PropertiesLoaderUtils.loadProperties(resource);
			} catch (IOException ignored) {
			}

			if (properties == null) {
				return merged;
			}

			merged.putAll(properties);

			for (Entry<Object, Object> entry : properties.entrySet()) {
				if (entry.getKey() instanceof String) {
					String key = (String) entry.getKey();

					for (String profile : profiles) {
						String prefix = profile + ".";
						int prefixLength = prefix.length();

						if (profile != null && StringUtils.startsWith(key, prefix)) {
							merged.put(StringUtils.substring(key, prefixLength), entry.getValue());
						}
					}
				}
			}

			return merged;
		}

		public static Properties mergeProperties(Set<String> profiles, Set<Resource> resources) {
			Properties merged = new Properties();

			if (!CollectionUtils.isEmpty(profiles)) {
				merged.setProperty(AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME, StringUtils.join(profiles, ","));
			}

			for (Resource resource : resources) {
				merged.putAll(mergeProperties(profiles, resource));
			}

			merged.putAll(System.getProperties());
			return merged;
		}

		public static String resolveProperty(Properties properties, String value) {
			Matcher matcher = PROPERTIES_PATTERN.matcher(value);
			StringBuffer sb = new StringBuffer();

			while (matcher.find()) {
				String property = matcher.group(2);
				String[] splits = StringUtils.split(property, ":", 2);
				String propertyValue = properties.getProperty(splits[0], splits.length == 1 ? null : splits[1]);

				if (StringUtils.isBlank(propertyValue)) {
					throw new IllegalArgumentException(splits[0] + " should be configured");
				}

				matcher.appendReplacement(sb, propertyValue);
			}

			matcher.appendTail(sb);
			return sb.toString();
		}

		@Override
		protected Properties mergeProperties() throws IOException {
			return mergeProperties(profiles, resources);
		}
	}
}
