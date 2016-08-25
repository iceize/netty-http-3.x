package ice.http.server.router;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.Request;
import ice.http.server.Settings;
import ice.http.server.SettingsAware;
import ice.http.server.action.Action;
import ice.http.server.action.NullAction;
import ice.http.server.annotations.Method;
import ice.http.server.utils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

public class HttpRouter implements SettingsAware, Router, PathExposure, InitializingBean, ApplicationContextAware {
	private Settings settings;
	private ApplicationContext applicationContext;
	private final List<Router> routers = Lists.newArrayList();
	private final Logger logger = LoggerFactory.getLogger(HttpRouter.class);

	@Override
	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Action route(Request request) {
		for (Router router : routers) {
			Action action = router.route(request);

			if (action != null && !(action instanceof NullAction)) {
				return action;
			}
		}

		return null;
	}

	@Override
	public Map<Method.HttpMethod, Map<String, Action>> getPaths() {
		Map<Method.HttpMethod, Map<String, Action>> paths = Maps.newTreeMap();

		for (Router router : routers) {
			if (router instanceof PathExposure) {
				Map<Method.HttpMethod, Map<String, Action>> subPaths = ((PathExposure) router).getPaths();

				if (!CollectionUtils.isEmpty(subPaths)) {
					for (Map.Entry<Method.HttpMethod, Map<String, Action>> entry : subPaths.entrySet()) {
						Map<String, Action> subPathMap = paths.get(entry.getKey());

						if (subPathMap == null) {
							subPathMap = Maps.newTreeMap();
						}

						subPathMap.putAll(entry.getValue());
						paths.put(entry.getKey(), subPathMap);
					}
				}
			}
		}

		return paths;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		List<Class<? extends Router>> routerClasses = Lists.newArrayList();
		routerClasses.add(StaticRouter.class);
		routerClasses.add(TreeRouter.class);

		for (Class<? extends Router> routerClass : routerClasses) {
			try {
				if (routerClass.isAnnotationPresent(Deprecated.class)) {
					continue;
				}

				Router router = BeanUtils.createBean(routerClass, settings, applicationContext);

				if (router != null) {
					routers.add(router);
				}
			} catch (Exception e) {
				logger.debug(e.getMessage(), e);
			}
		}

		if (logger.isDebugEnabled()) {
			for (Router router : routers) {
				logger.debug("[router] {}", router.getClass());
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
