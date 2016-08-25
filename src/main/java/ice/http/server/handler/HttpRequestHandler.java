package ice.http.server.handler;

import ice.http.server.*;
import ice.http.server.action.Action;
import ice.http.server.dispatcher.Dispatcher;
import ice.http.server.exception.ContentParseException;
import ice.http.server.param.Validation;
import ice.http.server.parser.ContentParser;
import ice.http.server.parser.Parser;
import ice.http.server.router.HttpRouter;
import ice.http.server.utils.BeanUtils;
import ice.http.server.websocket.WebSocketRequestHandler;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class HttpRequestHandler extends SimpleChannelUpstreamHandler implements SettingsAware, ApplicationContextAware {
	private Request request;
	private Response response;
	private Settings settings;
	private Parser parser;
	private boolean ready;
	private boolean readingChunks;
	private HttpRouter router;
	private Dispatcher dispatcher;
	private ContentParser contentParser;
	private HttpResponseHandler httpResponseHandler;
	private HttpExceptionHandler httpExceptionHandler;
	private WebSocketRequestHandler webSocketRequestHandler;
	private final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public void messageReceived(ChannelHandlerContext context, MessageEvent messageEvent) throws Exception {
		Object message = messageEvent.getMessage();

		if (!readingChunks) {
			HttpRequest httpRequest = (HttpRequest) message;

			if (HttpHeaders.Values.WEBSOCKET.equalsIgnoreCase(httpRequest.headers().get(HttpHeaders.Names.UPGRADE))) {
				webSocketRequestHandler.handshake(context, httpRequest, messageEvent, this);
				return;
			}

			request = Request.create(httpRequest, messageEvent);
			response = Response.create(httpRequest);

			Request.REQUEST_HOLDER.set(request);

			if (parser != null) {
				parser.close();
				parser = null;
			}

			parser = contentParser.getParser(request.contentType);

			try {
				parser.init(httpRequest);
			} catch (ContentParseException e) {
				logger.debug(e.getMessage(), e);
				Channels.close(messageEvent.getChannel());
				return;
			}

			if (httpRequest.isChunked()) {
				readingChunks = true;
			} else {
				ready = true;
			}
		} else {
			HttpChunk httpChunk = (HttpChunk) message;

			try {
				parser.offer(httpChunk);
			} catch (ContentParseException e) {
				logger.debug(e.getMessage(), e);
				Channels.close(messageEvent.getChannel());
				return;
			}

			if (httpChunk.isLast()) {
				ready = true;
				readingChunks = false;
			}
		}

		if (ready) {
			ready = false;
			handleMessage(context, messageEvent);
		}
	}

	private void handleMessage(ChannelHandlerContext context, MessageEvent messageEvent) {
		Validation.init();
		context.getChannel().setAttachment(response);

		try {
			parser.parse(request);

			try {
				Action action = router.route(request);
				dispatcher.dispatch(context, action, request, response);
			} catch (Throwable e) {
				response.cause = e;
				throw e;
			}

			context.getPipeline().addLast("response", httpResponseHandler);
		} catch (Throwable e) {
			logger.debug(e.getMessage(), e);
			context.getPipeline().addLast("exception", httpExceptionHandler);
		}

		context.sendUpstream(messageEvent);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent exceptionEvent) throws Exception {
		context.getChannel().close();
	}

	@Override
	public void channelOpen(ChannelHandlerContext context, ChannelStateEvent channelStateEvent) throws Exception {
		HttpServer.CHANNEL_GROUP.add(context.getChannel());
		super.channelOpen(context, channelStateEvent);
	}

	@Override
	public void channelClosed(ChannelHandlerContext context, ChannelStateEvent channelStateEvent) throws Exception {
		parser.close();
		parser = null;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.router = BeanUtils.getBean(settings, applicationContext, HttpRouter.class);
		this.dispatcher = BeanUtils.getBean(settings, applicationContext, Dispatcher.class);
		this.contentParser = BeanUtils.getBean(settings, applicationContext, ContentParser.class);
		this.httpResponseHandler = BeanUtils.getBean(settings, applicationContext, HttpResponseHandler.class);
		this.httpExceptionHandler = BeanUtils.getBean(settings, applicationContext, HttpExceptionHandler.class);
		this.webSocketRequestHandler = BeanUtils.getBean(settings, applicationContext, WebSocketRequestHandler.class);
	}
}
