package ice.http.server;

import com.google.common.collect.Sets;
import ice.http.server.handler.HttpExceptionHandler;
import ice.http.server.handler.HttpResponseHandler;
import ice.http.server.handler.HttpTimeoutHandler;
import ice.http.server.utils.BeanUtils;
import ice.http.server.utils.NamedThreadFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ServerTemplate implements Server, SettingsAware, InitializingBean, ApplicationContextAware, BeanFactoryPostProcessor {
	protected Settings settings;
	protected ServerBootstrap bootstrap;
	protected ApplicationContext applicationContext;
	protected ExecutorService bossExecutors;
	protected ExecutorService workerExecutors;
	private final AtomicBoolean running = new AtomicBoolean();
	private final Logger logger = LoggerFactory.getLogger(ServerTemplate.class);

	public Settings getSettings() {
		return settings;
	}

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	protected abstract ChannelPipelineFactory getChannelPipelineFactory();

	protected abstract void serverBound(Channel channel);

	protected abstract void serverShutdown();

	public void bind() {
		bossExecutors = Executors.newCachedThreadPool(new NamedThreadFactory("boss@" + settings.getName()));
		workerExecutors = Executors.newCachedThreadPool(new NamedThreadFactory("worker@" + settings.getName()));

		NioServerSocketChannelFactory channelFactory;

		if (settings.getThreadCount() == 0) {
			channelFactory = new NioServerSocketChannelFactory(bossExecutors, workerExecutors);
		} else {
			channelFactory = new NioServerSocketChannelFactory(bossExecutors, workerExecutors, settings.getThreadCount());
		}

		bootstrap = new ServerBootstrap(channelFactory);
		bootstrap.setPipelineFactory(getChannelPipelineFactory());
		bootstrap.setOption("backlog", settings.getBacklog());
		bootstrap.setOption("child.tcpNoDelay", settings.isUseTcpNoDelay());
		bootstrap.setOption("child.keepAlive", settings.isKeepAlive());
		bootstrap.setOption("child.soLinger", settings.getSoLinger());
		bootstrap.setOption("reuseAddress", settings.isReuseAddress());
		bootstrap.setOption("connectTimeoutMillis", settings.getConnectTimeoutMillis());
		bootstrap.setOption("receiveBufferSize", settings.getReceiveBufferSize());

		logger.debug("settings={}", settings);

		Channel channel = bootstrap.bind(new InetSocketAddress(settings.getPort()));
		serverBound(channel);

		while (!channel.isOpen()) {
			channel.getCloseFuture().awaitUninterruptibly();
		}

		running.set(true);
		logger.info("{}({}) is ready...", this.getClass().getSimpleName(), settings.getPort());
	}

	@Override
	public void stop() {
		serverShutdown();

		if (bossExecutors != null) {
			bossExecutors.shutdown();
		}

		if (workerExecutors != null) {
			workerExecutors.shutdown();
		}

		if (bootstrap != null) {
			bootstrap.getFactory().releaseExternalResources();
		}

		running.set(false);
	}

	@Override
	public boolean running() {
		return running.get();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(settings, "settings should be configured");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	protected abstract Class<?>[] getServerComponents();

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) configurableListableBeanFactory;

		Class<?>[] handlers = {HttpTimeoutHandler.class, HttpResponseHandler.class, HttpExceptionHandler.class};
		Class<?>[] serverComponents = getServerComponents();

		Set<Class<?>> beanClasses = Sets.newHashSet();
		Collections.addAll(beanClasses, handlers);

		if (serverComponents != null) {
			Collections.addAll(beanClasses, serverComponents);
		}

		for (Class<?> beanClass : beanClasses) {
			BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(beanClass);

			if (SettingsAware.class.isAssignableFrom(beanClass)) {
				beanDefinitionBuilder.addPropertyValue("settings", settings);
			}

			beanFactory.registerBeanDefinition(BeanUtils.beanName(settings, beanClass), beanDefinitionBuilder.getBeanDefinition());
			logger.debug("{}: {} is registered...", settings.getName(), BeanUtils.beanName(settings, beanClass));
		}
	}
}
