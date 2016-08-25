package ice.http.server.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpTimeoutHandler extends IdleStateAwareChannelHandler {
	private final Logger logger = LoggerFactory.getLogger(HttpTimeoutHandler.class);

	@Override
	public void channelIdle(ChannelHandlerContext context, IdleStateEvent idleStateEvent) {
		final String remoteAddress = idleStateEvent.getChannel().getRemoteAddress().toString();

		idleStateEvent.getChannel().close().addListener(future -> {
			if (future.isSuccess()) {
				logger.debug("channel closed: {}", remoteAddress);
			} else {
				logger.debug("channel failed to close: {}", remoteAddress);
			}
		});
	}
}
