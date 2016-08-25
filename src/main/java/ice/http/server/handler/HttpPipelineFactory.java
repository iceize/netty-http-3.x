package ice.http.server.handler;

import ice.http.server.Settings;
import ice.http.server.utils.BeanUtils;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class HttpPipelineFactory implements ChannelPipelineFactory, ApplicationContextAware {
	private Timer timer;
	private Settings settings;
	private ApplicationContext applicationContext;
	private HttpTimeoutHandler httpTimeoutHandler;

	public HttpPipelineFactory(Timer timer, Settings settings) {
		this.timer = timer;
		this.settings = settings;
	}

	@Override
	public ChannelPipeline getPipeline() throws Exception {
		HttpRequestHandler httpRequestHandler = new HttpRequestHandler();
		httpRequestHandler.setSettings(settings);
		httpRequestHandler.setApplicationContext(applicationContext);

		ChannelPipeline pipeline = Channels.pipeline();

		if (settings.getKeepAliveTimeout() > 0) {
			pipeline.addLast("idle", new IdleStateHandler(timer, 0, 0, settings.getKeepAliveTimeout()));
			pipeline.addLast("timeout", httpTimeoutHandler);
		}

		pipeline.addLast("decoder", new HttpRequestDecoder());
		pipeline.addLast("encoder", new HttpResponseEncoder());
		pipeline.addLast("handler", httpRequestHandler);

		return pipeline;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		this.httpTimeoutHandler = BeanUtils.getBean(settings, applicationContext, HttpTimeoutHandler.class);
	}
}
