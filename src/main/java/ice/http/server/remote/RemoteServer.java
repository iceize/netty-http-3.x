package ice.http.server.remote;

import ice.http.server.ServerTemplate;
import ice.http.server.action.InterceptorManager;
import ice.http.server.dispatcher.Dispatcher;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

public class RemoteServer extends ServerTemplate {
	private final Timer timer = new HashedWheelTimer();
	static final ChannelGroup CHANNEL_GROUP = new DefaultChannelGroup(RemoteServer.class.getSimpleName());

	@Override
	protected ChannelPipelineFactory getChannelPipelineFactory() {
		RemotePipelineFactory remotePipelineFactory = new RemotePipelineFactory(timer, settings);
		remotePipelineFactory.setApplicationContext(applicationContext);
		return remotePipelineFactory;
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
		String extensions = settings.getExtensions();
		String packageName = this.getClass().getPackage().getName();

		if (extensions == null) {
			settings.setExtensions(packageName);
		} else {
			settings.setExtensions(extensions + "," + packageName);
		}

		return new Class<?>[]{RemoteRouter.class, InterceptorManager.class, Dispatcher.class};
	}
}
