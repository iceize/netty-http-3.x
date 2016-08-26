package ice.http.server.dispatcher;

import com.google.common.collect.Maps;
import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
import ice.http.server.action.Action;
import ice.http.server.action.NullAction;
import ice.http.server.utils.BeanUtils;
import ice.http.server.utils.ClassPathScanner;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Dispatcher implements SettingsAware, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private ApplicationContext applicationContext;
	private final Map<Class<? extends Action>, ActionDispatcher> dispatchers = Maps.newHashMap();
	private final Logger logger = LoggerFactory.getLogger(Dispatcher.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public void dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		Class<? extends Action> actionClass = action == null ? NullAction.class : action.getClass();
		ActionDispatcher actionDispatcher = dispatchers.get(actionClass);

		if (actionDispatcher == null) {
			throw new IllegalStateException("dispatcher is not found");
		}

		actionDispatcher.dispatch(context, action, request, response);
	}

	private void registerDispatcher(String basePackage) {
		Set<Class<? extends ActionDispatcher>> dispatcherClasses = ClassPathScanner.scan(basePackage, new ClassPathScanner.AssignableFilter(ActionDispatcher.class));

		for (Class<? extends ActionDispatcher> dispatcherClass : dispatcherClasses) {
			try {
				ActionDispatcher actionDispatcher = BeanUtils.createBean(dispatcherClass, settings, applicationContext);

				if (actionDispatcher != null) {
					dispatchers.put(actionDispatcher.assignableFrom(), actionDispatcher);
				}
			} catch (Exception e) {
				logger.debug(e.getMessage(), e);
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<Class<? extends Action>, ActionDispatcher> entry : dispatchers.entrySet()) {
				logger.debug("[dispatcher] {} => {}", entry.getKey().getSimpleName(), entry.getValue().getClass());
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		registerDispatcher(this.getClass().getPackage().getName());

		if (settings.getExtensions() != null) {
			for (String basePackage : StringUtils.split(settings.getExtensions(), ",")) {
				registerDispatcher(basePackage);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
