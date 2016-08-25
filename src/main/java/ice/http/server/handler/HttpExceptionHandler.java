package ice.http.server.handler;

import ice.http.server.Response;
import ice.http.server.exception.NotFoundException;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class HttpExceptionHandler extends HttpNullableResponseHandler implements ApplicationContextAware {
	private HttpNotFoundHandler httpNotFoundHandler;
	private final Logger logger = LoggerFactory.getLogger(HttpExceptionHandler.class);

	@Override
	protected void handleHttpResponse(ChannelHandlerContext context, Response response) {
		Throwable cause = response.cause;

		if (cause != null) {
			logger.debug(cause.getMessage(), cause);
		}

		byte[] bytes = response.output;
		HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;

		if (cause != null && (cause instanceof NotFoundException)) {
			if (httpNotFoundHandler == null) {
				status = HttpResponseStatus.NOT_FOUND;
			} else {
				Response handledResponse = httpNotFoundHandler.handleNotFound(response.requestPath, response.contentType);
				status = handledResponse.status;
				bytes = handledResponse.output;

				if (StringUtils.isNotBlank(handledResponse.encoding)) {
					response.encoding = handledResponse.encoding;
				}

				if (StringUtils.isNotBlank(handledResponse.contentType)) {
					response.contentType = handledResponse.contentType;
				}
			}
		}

		HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);

		if (status == HttpResponseStatus.MOVED_PERMANENTLY) {
			httpResponse.headers().set(HttpHeaders.Names.LOCATION, new String(bytes));
		} else if (bytes == null || bytes.length == 0) {
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, "0");
			httpResponse.setContent(ChannelBuffers.EMPTY_BUFFER);
		} else {
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, response.contentType + "; charset=" + response.encoding);
			httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(bytes.length));
			httpResponse.setContent(ChannelBuffers.copiedBuffer(bytes));
		}

		httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
		context.getChannel().write(httpResponse).addListener(ChannelFutureListener.CLOSE);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		try {
			httpNotFoundHandler = applicationContext.getBean(HttpNotFoundHandler.class);
		} catch (BeansException e) { // ignore
		}
	}
}
