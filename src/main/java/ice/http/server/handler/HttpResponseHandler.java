package ice.http.server.handler;

import ice.http.server.Cookie;
import ice.http.server.Header;
import ice.http.server.Response;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.*;

public class HttpResponseHandler extends HttpNullableResponseHandler {
	@Override
	protected void handleHttpResponse(ChannelHandlerContext context, Response response) {
		byte[] bytes = null;
		HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status);

		if (response.output != null) {
			bytes = response.output;
		}

		for (Header header : response.headers.values()) {
			httpResponse.headers().set(header.name, header.value());
		}

		httpResponse.headers().remove(HttpHeaders.Names.CONTENT_TYPE);

		if (response.status != HttpResponseStatus.MOVED_PERMANENTLY) {
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, response.contentType + "; charset=" + response.encoding);
		}

		for (Cookie cookie : response.cookies.values()) {
			httpResponse.headers().add(HttpHeaders.Names.SET_COOKIE, cookie.encode());
		}

		if (bytes == null) {
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, "0");
			httpResponse.setContent(ChannelBuffers.EMPTY_BUFFER);
		} else {
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(bytes.length));
			httpResponse.setContent(ChannelBuffers.copiedBuffer(bytes));
		}

		if (response.shouldKeepAlive) {
			httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
			context.getChannel().write(httpResponse);
		} else {
			httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
			context.getChannel().write(httpResponse).addListener(ChannelFutureListener.CLOSE);
		}
	}
}
