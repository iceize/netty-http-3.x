package ice.http.server.websocket;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
import ice.http.server.action.Action;
import ice.http.server.annotations.Method;
import ice.http.server.parser.Parser;
import ice.http.server.router.HttpRouter;
import ice.http.server.utils.BeanUtils;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class WebSocketRequestHandler extends SimpleChannelUpstreamHandler implements SettingsAware, ApplicationContextAware {
	private Settings settings;
	private HttpRouter router;
	private WebSocketDispatcher dispatcher;
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public void handshake(ChannelHandlerContext context, HttpRequest httpRequest, MessageEvent messageEvent, ChannelHandler requestHandler) {
		if (httpRequest.getMethod() != HttpMethod.GET) {
			context.getChannel().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)).addListener(ChannelFutureListener.CLOSE);
			return;
		}

		QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.getUri());
		String path = queryStringDecoder.getPath();
		WebSocketServerHandshakerFactory webSocketServerHandshakerFactory = new WebSocketServerHandshakerFactory("ws://" + httpRequest.headers().get(HttpHeaders.Names.HOST) + path, null, false);
		WebSocketServerHandshaker webSocketServerHandshaker = webSocketServerHandshakerFactory.newHandshaker(httpRequest);

		if (webSocketServerHandshaker == null) {
			webSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(context.getChannel());
		} else {
			webSocketServerHandshaker.handshake(context.getChannel(), httpRequest).addListener(WebSocketServerHandshaker.HANDSHAKE_LISTENER);

			Request request = Request.create(httpRequest, messageEvent);
			request.method = Method.HttpMethod.WS;
			request.args.put("handshaker", webSocketServerHandshaker);

			dispatcher.registerCallback(router.route(request), dispatcher.registerChannel(path, context.getChannel(), request), request);

			context.getChannel().setAttachment(request);
			context.getPipeline().addBefore("handler", "aggregator", new HttpChunkAggregator(settings.getWebSocketMaxContentLength()));

			try {
				context.getPipeline().replace(requestHandler, "handler", this);
			} catch (Exception ignored) {
			}
		}
	}

	@Override
	public void messageReceived(ChannelHandlerContext context, MessageEvent messageEvent) throws Exception {
		Object message = messageEvent.getMessage();
		Request request = (Request) context.getChannel().getAttachment();

		if (message instanceof CloseWebSocketFrame) {
			WebSocketServerHandshaker webSocketServerHandshaker = (WebSocketServerHandshaker) request.args.get("handshaker");
			webSocketServerHandshaker.close(context.getChannel(), (CloseWebSocketFrame) message);
			return;
		}

		if (message instanceof PingWebSocketFrame) {
			context.getChannel().write(new PongWebSocketFrame(((WebSocketFrame) message).getBinaryData()));
			return;
		}

		if (!(message instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(message.getClass().getName() + " frame types not supported");
		}

		request.args.put(Parser.BODY, ((TextWebSocketFrame) message).getText());
		Response response = new Response();

		Action action = router.route(request);
		Object value = dispatcher.dispatch(context, action, request, response);
		context.getChannel().write(new TextWebSocketFrame(MAPPER.writeValueAsString(value)));
	}

	@Override
	public void channelClosed(ChannelHandlerContext context, ChannelStateEvent channelStateEvent) throws Exception {
		Request request = (Request) context.getChannel().getAttachment();
		dispatcher.unregisterChannel(request.path, channelStateEvent.getChannel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent exceptionEvent) throws Exception {
		exceptionEvent.getChannel().close();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.router = BeanUtils.getBean(settings, applicationContext, HttpRouter.class);
		this.dispatcher = BeanUtils.getBean(settings, applicationContext, WebSocketDispatcher.class);
	}
}
