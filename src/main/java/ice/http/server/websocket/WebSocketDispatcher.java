package ice.http.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.*;
import ice.http.server.action.Action;
import ice.http.server.action.Interceptor;
import ice.http.server.action.InterceptorManager;
import ice.http.server.action.MethodAction;
import ice.http.server.annotations.*;
import ice.http.server.annotations.Method.HttpMethod;
import ice.http.server.binder.BinderManager;
import ice.http.server.utils.BeanUtils;
import ice.http.server.utils.NamedThreadFactory;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

public class WebSocketDispatcher implements SettingsAware, ApplicationContextAware, InitializingBean, DisposableBean {
	private Settings settings;
	private BinderManager binderManager;
	private InterceptorManager interceptorManager;
	private Map<String, Object> controllers;
	private ExecutorService executorService;
	private final List<MethodAction> onRequestMethods = Lists.newArrayList();
	private final ChannelGroup channelGroup = new DefaultChannelGroup(this.getClass().getSimpleName());
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	Channel registerChannel(String path, Channel channel, Request request) {
		final ChannelWrapper channelWrapper = new ChannelWrapper(path, channel);
		channelGroup.add(channelWrapper);
		return channelWrapper;
	}

	boolean unregisterChannel(String path, Channel channel) {
		return channelGroup.remove(new ChannelWrapper(path, channel));
	}

	private boolean isBound(String path) {
		for (Channel channel : channelGroup) {
			if (channel.isConnected() && (channel instanceof ChannelWrapper)) {
				return true;
			}
		}

		return false;
	}

	private boolean sendMessage(String path, Object message, Channel channel) {
		if (channel.isConnected() && (channel instanceof ChannelWrapper)) {
			ChannelWrapper channelWrapper = (ChannelWrapper) channel;

			if (path.equals(channelWrapper.path)) {
				String msg = null;

				if (message != null) {
					try {
						msg = (message instanceof String) ? (String) message : MAPPER.writeValueAsString(message);
					} catch (JsonProcessingException ignored) {
					}
				}

				channel.write(new TextWebSocketFrame(msg));
				return true;
			}
		}

		return false;
	}

	public boolean sendMessage(String path, Object message) {
		boolean sent = false;

		for (Channel channel : channelGroup) {
			try {
				if (sendMessage(path, message, channel)) {
					sent = true;
				}
			} catch (Exception ignored) {
			}
		}

		return sent;
	}

	private Map<String, List<String>> getPathVariables(MethodAction methodAction, String requestPath) {
		String[] paths = StringUtils.split(methodAction.path(), "/");
		String[] requestPaths = StringUtils.split(requestPath, "/");
		Map<String, List<String>> pathVariables = Maps.newHashMap();

		for (int i = 0; i < paths.length; i++) {
			Matcher matcher = Context.PATH_VARIABLE_PATTERN.matcher(paths[i]);

			if (matcher.find()) {
				pathVariables.put(matcher.group(1), Lists.newArrayList(requestPaths[i]));
			}
		}

		return pathVariables;
	}

	private Object invoke(MethodAction methodAction, Request request, Response response) {
		if (CollectionUtils.isEmpty(methodAction.parameters())) {
			return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean());
		}

		List<Object> args = Lists.newArrayList();
		Map<String, List<String>> requestParams = Maps.newHashMap(request.params);
		requestParams.putAll(getPathVariables(methodAction, request.path));

		for (Entry<String, Parameter> entry : methodAction.parameters().entrySet()) {
			args.add(binderManager.bind(request, response, entry.getValue(), requestParams));
		}

		return ReflectionUtils.invokeMethod(methodAction.method(), methodAction.bean(), args.toArray(new Object[args.size()]));
	}

	public Object dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		MethodAction methodAction = (MethodAction) action;
		request.actionMethod = methodAction.method();

		Map<Class<? extends Annotation>, Set<Interceptor>> interceptors = methodAction.interceptors();

		try {
			interceptorManager.intercept(methodAction, interceptors.get(Before.class), request, response);
			Object result = invoke(methodAction, request, response);
			interceptorManager.intercept(methodAction, interceptors.get(After.class), request, response);
			return result;
		} catch (Exception e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			Interceptor interceptor = interceptorManager.findProperInterceptor(interceptors.get(Catch.class), cause);

			if (interceptor == null) {
				throw e;
			}

			return interceptorManager.intercept(interceptor, request, response, cause);
		} finally {
			interceptorManager.intercept(methodAction, interceptors.get(Finally.class), request, response);
		}
	}

	void registerCallback(Action action, final Channel channel, final Request request) {
		if (action == null || !(action instanceof MethodAction)) {
			return;
		}

		final MethodAction methodAction = (MethodAction) action;

		executorService.submit(new Runnable() {
			@Override
			public void run() {
				while (true) {
					if (!isBound(request.path)) {
						break;
					}

					try {
						Object message = invoke(methodAction, request, null);
						sendMessage(request.path, message, channel);
						Thread.sleep(methodAction.method().getAnnotation(WS.class).delay());
					} catch (Exception ignored) {
					}
				}
			}
		});
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		executorService = Executors.newCachedThreadPool(new NamedThreadFactory(this.getClass().getSimpleName()));

		for (final Entry<String, Object> entry : controllers.entrySet()) {
			Method[] methods = entry.getValue().getClass().getDeclaredMethods();

			for (final Method method : methods) {
				if (!Modifier.isPublic(method.getModifiers())) {
					continue;
				}

				WS ws = method.getAnnotation(WS.class);

				if (ws == null) {
					continue;
				}

				String[] paths = ws.value();
				final long delay = ws.delay();
				WS.Event event = ws.event();

				if (ArrayUtils.isEmpty(paths) || delay <= 0L) {
					continue;
				}

				if (event == WS.Event.request) {
					for (String path : paths) {
						onRequestMethods.add(new MethodAction(entry.getValue(), method, HttpMethod.WS, path, null));
					}

					continue;
				}

				final List<Object> args = Lists.newArrayList();

				for (Class<?> clazz : method.getParameterTypes()) {
					if (clazz == boolean.class) {
						args.add(false);
					} else if (clazz == char.class) {
						args.add('\u0000');
					} else if (clazz == byte.class || clazz == short.class || clazz == int.class) {
						args.add(0);
					} else if (clazz == long.class) {
						args.add(0L);
					} else if (clazz == float.class) {
						args.add(0.0F);
					} else if (clazz == double.class) {
						args.add(0.0);
					} else {
						args.add(null);
					}
				}

				for (final String path : paths) {
					executorService.submit(new Runnable() {
						@Override
						public void run() {
							while (true) {
								if (isBound(path)) {
									Object message = args.isEmpty() ? ReflectionUtils.invokeMethod(method, entry.getValue()) : ReflectionUtils.invokeMethod(method, entry.getValue(), args.toArray(new Object[args.size()]));
									sendMessage(path, message);
								}

								try {
									Thread.sleep(delay);
								} catch (InterruptedException ignored) {
								}
							}
						}
					});
				}
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		if (executorService != null) {
			executorService.shutdown();
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.controllers = applicationContext.getBeansWithAnnotation(Controller.class);
		this.binderManager = BeanUtils.getBean(settings, applicationContext, BinderManager.class);
		this.interceptorManager = BeanUtils.getBean(settings, applicationContext, InterceptorManager.class);
	}

	static class ChannelWrapper implements Channel {
		private final String path;
		private final Channel channel;

		public ChannelWrapper(String path, Channel channel) {
			this.path = path;
			this.channel = channel;
		}

		@Override
		public int compareTo(Channel o) {
			return channel.compareTo(o);
		}

		@Override
		public Integer getId() {
			return channel.getId();
		}

		@Override
		public ChannelFactory getFactory() {
			return channel.getFactory();
		}

		@Override
		public Channel getParent() {
			return channel.getParent();
		}

		@Override
		public ChannelConfig getConfig() {
			return channel.getConfig();
		}

		@Override
		public ChannelPipeline getPipeline() {
			return channel.getPipeline();
		}

		@Override
		public boolean isOpen() {
			return channel.isOpen();
		}

		@Override
		public boolean isBound() {
			return channel.isBound();
		}

		@Override
		public boolean isConnected() {
			return channel.isConnected();
		}

		@Override
		public SocketAddress getLocalAddress() {
			return channel.getLocalAddress();
		}

		@Override
		public SocketAddress getRemoteAddress() {
			return channel.getRemoteAddress();
		}

		@Override
		public ChannelFuture write(Object message) {
			return channel.write(message);
		}

		@Override
		public ChannelFuture write(Object message, SocketAddress remoteAddress) {
			return channel.write(message, remoteAddress);
		}

		@Override
		public ChannelFuture bind(SocketAddress localAddress) {
			return channel.bind(localAddress);
		}

		@Override
		public ChannelFuture connect(SocketAddress remoteAddress) {
			return channel.connect(remoteAddress);
		}

		@Override
		public ChannelFuture disconnect() {
			return channel.disconnect();
		}

		@Override
		public ChannelFuture unbind() {
			return channel.unbind();
		}

		@Override
		public ChannelFuture close() {
			return channel.close();
		}

		@Override
		public ChannelFuture getCloseFuture() {
			return channel.getCloseFuture();
		}

		@Override
		public int getInterestOps() {
			return channel.getInterestOps();
		}

		@Override
		public boolean isReadable() {
			return channel.isReadable();
		}

		@Override
		public boolean isWritable() {
			return channel.isWritable();
		}

		@Override
		public ChannelFuture setInterestOps(int interestOps) {
			return channel.setInterestOps(interestOps);
		}

		@Override
		public ChannelFuture setReadable(boolean readable) {
			return channel.setReadable(readable);
		}

		@Override
		public Object getAttachment() {
			return channel.getAttachment();
		}

		@Override
		public void setAttachment(Object attachment) {
			channel.setAttachment(attachment);
		}

		@Override
		public int hashCode() {
			return channel.getId().hashCode();
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

			ChannelWrapper other = (ChannelWrapper) obj;
			return channel.getId().equals(other.channel.getId());
		}
	}
}
