package ice.http.server.handler;

import ice.http.server.Response;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpNullableResponseHandler extends SimpleChannelUpstreamHandler {
	private final Logger logger = LoggerFactory.getLogger(HttpNullableResponseHandler.class);

	protected abstract void handleHttpResponse(ChannelHandlerContext context, Response response);

	@Override
	public void messageReceived(ChannelHandlerContext context, MessageEvent messageEvent) throws Exception {
		context.getChannel().getPipeline().remove(this);
		Response response = (Response) context.getChannel().getAttachment();

		if (response == null) {
			logger.debug("response is null");
			HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, "0");
			httpResponse.setContent(ChannelBuffers.EMPTY_BUFFER);
			httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
			context.getChannel().write(httpResponse).addListener(ChannelFutureListener.CLOSE);

			return;
		}

		handleHttpResponse(context, response);
	}
}
