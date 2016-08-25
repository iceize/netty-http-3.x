package ice.http.server.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import ice.http.server.*;
import ice.http.server.action.Action;
import ice.http.server.dispatcher.Dispatcher;
import ice.http.server.exception.NotFoundException;
import ice.http.server.handler.HttpExceptionHandler;
import ice.http.server.handler.HttpResponseHandler;
import ice.http.server.parser.Parser;
import ice.http.server.utils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class RemoteRequestHandler extends SimpleChannelUpstreamHandler implements SettingsAware, ApplicationContextAware {
	private Request request;
	private Response response;
	private Settings settings;
	private boolean ready;
	private boolean readingChunks;
	private RemoteRouter router;
	private Dispatcher dispatcher;
	private HttpResponseHandler httpResponseHandler;
	private HttpExceptionHandler httpExceptionHandler;
	private final StringBuffer content = new StringBuffer();

	static final String USE_PARAMETER_NAMES = "useParameterNames";
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	private final Logger logger = LoggerFactory.getLogger(RemoteRequestHandler.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public void messageReceived(ChannelHandlerContext context, MessageEvent messageEvent) throws Exception {
		if (!readingChunks) {
			HttpRequest httpRequest = (HttpRequest) messageEvent.getMessage();

			request = Request.create(httpRequest, messageEvent);
			response = new Response();

			content.setLength(0);
			content.append(httpRequest.getContent().toString(Context.DEFAULT_CHARSET));

			if (httpRequest.isChunked()) {
				readingChunks = true;
			} else {
				ready = true;
			}
		} else {
			HttpChunk httpChunk = (HttpChunk) messageEvent.getMessage();
			content.append(httpChunk.getContent().toString(Context.DEFAULT_CHARSET));

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
		context.getChannel().setAttachment(response);

		try {
			String requestBody = this.content.toString();
			boolean useParameterNames = "true".equalsIgnoreCase(request.header(RemoteHeaderNames.REMOTE_USE_PARAMETER_NAMES));

			request.args.put(Parser.BODY, requestBody);
			request.args.put(USE_PARAMETER_NAMES, useParameterNames);

			if (StringUtils.isEmpty(requestBody)) {
				requestBody = useParameterNames ? "{}" : "[]";
				request.args.put(Parser.BODY, requestBody);
			}

			response.contentType = "application/json";

			try {
				Action action = router.route(request);
				dispatcher.dispatch(context, action, request, response);
			} catch (Exception e) {
				response.cause = e;
				response.output = getExceptionMessage(context, request, response, e);
				throw e;
			}

			context.getPipeline().addLast("response", httpResponseHandler);
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			context.getPipeline().addLast("exception", httpExceptionHandler);
		}

		context.sendUpstream(messageEvent);
	}

	private byte[] getExceptionMessage(ChannelHandlerContext context, Request request, Response response, Throwable cause) {
		if (cause instanceof NotFoundException) {
			return null;
		}

		Map<String, String> map = Maps.newLinkedHashMap();
		map.put("type", cause.getClass().getName());

		if (cause.getMessage() != null) {
			map.put("message", cause.getMessage());
		}

		StringWriter writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		cause.printStackTrace(printWriter);
		printWriter.flush();

		map.put("stacktrace", writer.toString());

		try {
			return MAPPER.writeValueAsBytes(map);
		} catch (JsonProcessingException ignored) {
		}

		return null;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent exceptionEvent) throws Exception {
		context.getChannel().close();
	}

	@Override
	public void channelOpen(ChannelHandlerContext context, ChannelStateEvent channelStateEvent) throws Exception {
		RemoteServer.CHANNEL_GROUP.add(context.getChannel());
		super.channelOpen(context, channelStateEvent);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.router = BeanUtils.getBean(settings, applicationContext, RemoteRouter.class);
		this.dispatcher = BeanUtils.getBean(settings, applicationContext, Dispatcher.class);
		this.httpResponseHandler = BeanUtils.getBean(settings, applicationContext, HttpResponseHandler.class);
		this.httpExceptionHandler = BeanUtils.getBean(settings, applicationContext, HttpExceptionHandler.class);
	}
}
