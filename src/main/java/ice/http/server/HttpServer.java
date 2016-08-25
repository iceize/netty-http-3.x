package ice.http.server;

import ice.http.server.action.InterceptorManager;
import ice.http.server.binder.BinderManager;
import ice.http.server.dispatcher.Dispatcher;
import ice.http.server.handler.HttpPipelineFactory;
import ice.http.server.param.ConverterManager;
import ice.http.server.param.ValidatorManager;
import ice.http.server.parser.ContentParser;
import ice.http.server.router.HttpRouter;
import ice.http.server.view.ViewResolver;
import ice.http.server.websocket.WebSocketDispatcher;
import ice.http.server.websocket.WebSocketRequestHandler;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

public class HttpServer extends ServerTemplate {
	private final Timer timer = new HashedWheelTimer();
	public static final ChannelGroup CHANNEL_GROUP = new DefaultChannelGroup(HttpServer.class.getSimpleName());

	@Override
	protected ChannelPipelineFactory getChannelPipelineFactory() {
		HttpPipelineFactory httpPipelineFactory = new HttpPipelineFactory(timer, settings);
		httpPipelineFactory.setApplicationContext(applicationContext);
		return httpPipelineFactory;
	}

	@Override
	protected void serverBound(Channel channel) {
		CHANNEL_GROUP.add(channel);
	}

	@Override
	protected void serverShutdown() {
		ChannelGroupFuture channelGroupFuture = CHANNEL_GROUP.close();
		channelGroupFuture.awaitUninterruptibly();
	}

	@Override
	protected Class<?>[] getServerComponents() {
		return new Class<?>[]{ContentParser.class, BinderManager.class, ConverterManager.class, ValidatorManager.class, ViewResolver.class, HttpRouter.class, InterceptorManager.class, Dispatcher.class, WebSocketDispatcher.class, WebSocketRequestHandler.class};
	}
}
