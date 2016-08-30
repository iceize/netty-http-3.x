package ice.http.server.router;

import com.google.common.collect.Maps;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import ice.http.server.Context;
import ice.http.server.Request;
import ice.http.server.action.Action;
import ice.http.server.action.NullAction;
import ice.http.server.action.StaticAction;
import ice.http.server.action.StatusAction;
import ice.http.server.annotations.Method;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StaticRouter implements Router, InitializingBean {
	private ConcurrentLinkedHashMap<String, StaticAction.StaticContent> staticFileCaches;
	private ConcurrentLinkedHashMap<String, Action> staticRouteCaches;
	private final Map<Method.HttpMethod, Map<String, String>> staticRoutes = Maps.newHashMap();
	private final Map<Method.HttpMethod, Map<String, HttpResponseStatus>> statusRoutes = Maps.newHashMap();
	private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
	private final Logger logger = LoggerFactory.getLogger(StaticRouter.class);

	private static final String DEFAULT_INDEX_FILE = "index.html";

	private Action redirect(String path) {
		return new StaticAction(path, new StaticAction.StaticContent(null, HttpResponseStatus.MOVED_PERMANENTLY.getCode()));
	}

	private Action routeStatic(String path, Map<String, String> staticRouteMap) {
		boolean directory = false;
		Entry<String, String> staticEntry = null;

		for (Entry<String, String> entry : staticRouteMap.entrySet()) {
			if (entry.getKey().endsWith(Context.PATH_DELIMITER)) { // dir
				if (path.startsWith(entry.getKey())) {
					directory = true;
					staticEntry = entry;
					break;
				}

				if ((path + Context.PATH_DELIMITER).equals(entry.getKey())) {
					return redirect(path);
				}
			} else {
				if (path.equals(entry.getKey())) {
					staticEntry = entry;
					break;
				}
			}
		}

		if (staticEntry == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder();

		if (directory) {
			sb.append(staticEntry.getValue());

			if (!staticEntry.getValue().endsWith(Context.PATH_DELIMITER)) {
				sb.append(Context.PATH_DELIMITER);
			}

			sb.append(path.substring(staticEntry.getKey().length()));
		} else {
			sb.append(staticEntry.getValue());
		}

		StaticAction.StaticContent staticContent = null;
		String staticPath = sb.toString();

		if (staticFileCaches.containsKey(path)) {
			staticContent = staticFileCaches.get(path);
		} else {
			if (!new ClassPathResource(staticPath).exists()) {
				return null;
			}

			if (staticPath.endsWith(Context.PATH_DELIMITER)) {
				staticPath += DEFAULT_INDEX_FILE;
			} else {
				try {
					Resource[] resources = resourcePatternResolver.getResources("classpath*:" + staticPath + "/*");

					if (resources.length > 0) {
						return redirect(staticPath);
					}
				} catch (IOException ignored) {
				}

				if (!new ClassPathResource(staticPath).exists()) {
					return null;
				}
			}

			InputStream inputStream = null;
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			try {
				inputStream = this.getClass().getResourceAsStream(staticPath);
				IOUtils.copyLarge(inputStream, outputStream);
				byte[] bytes = outputStream.toByteArray();

				staticContent = new StaticAction.StaticContent(bytes, System.currentTimeMillis());

				if (bytes.length <= 1024 * 1024) { // less than 1MB
					staticFileCaches.putIfAbsent(path, staticContent);
				}
			} catch (NullPointerException | IOException ignored) {
				return null;
			} finally {
				IOUtils.closeQuietly(inputStream);
				IOUtils.closeQuietly(outputStream);
			}
		}

		return new StaticAction(staticPath, staticContent);
	}

	@Override
	public Action route(Request request) {
		String routeKey = request.method.name() + " " + request.path;

		if (staticRouteCaches.containsKey(routeKey)) {
			return staticRouteCaches.get(routeKey);
		}

		Map<String, HttpResponseStatus> statusRouteMap = statusRoutes.get(request.method);

		if (statusRouteMap != null) {
			HttpResponseStatus httpResponseStatus = statusRouteMap.get(request.path);

			if (httpResponseStatus != null) {
				Action action = new StatusAction(request.path, httpResponseStatus);
				staticRouteCaches.putIfAbsent(routeKey, action);
				return action;
			}
		}

		Map<String, String> staticRouteMap = staticRoutes.get(request.method);

		if (staticRouteMap != null) {
			Action action = routeStatic(request.path, staticRouteMap);

			if (action != null) {
				staticRouteCaches.putIfAbsent(routeKey, action);
				return action;
			}
		}

		staticRouteCaches.putIfAbsent(routeKey, new NullAction());

		return null;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		int staticFileCacheSize = Integer.parseInt(System.getProperty("cache.static.file", "50"));
		int staticRouteCacheSize = Integer.parseInt(System.getProperty("cache.static.route", "1000"));

		staticFileCaches = new ConcurrentLinkedHashMap.Builder<String, StaticAction.StaticContent>().maximumWeightedCapacity(staticFileCacheSize).build();
		staticRouteCaches = new ConcurrentLinkedHashMap.Builder<String, Action>().maximumWeightedCapacity(staticRouteCacheSize).build();

		InputStream inputStream = this.getClass().getResourceAsStream("/routes");

		if (inputStream == null) {
			return;
		}

		List<String> lines = null;

		try {
			lines = IOUtils.readLines(inputStream, Context.DEFAULT_CHARSET);
		} catch (IOException ignored) {
		}

		if (CollectionUtils.isEmpty(lines)) {
			return;
		}

		for (String line : lines) {
			if (line == null || "".equals(line) || line.startsWith("#")) {
				continue;
			}

			String[] splits = StringUtils.split(line);

			if (splits.length != 3) {
				continue;
			}

			String path = splits[1];
			Method.HttpMethod httpMethod = Method.HttpMethod.valueOf(splits[0]);

			if (StringUtils.isNumeric(splits[2])) {
				Map<String, HttpResponseStatus> map = statusRoutes.get(httpMethod);

				if (map == null) {
					map = Maps.newLinkedHashMap();
				}

				map.put(path, HttpResponseStatus.valueOf(Integer.parseInt(splits[2])));
				statusRoutes.put(httpMethod, map);
			}

			if (splits[2].startsWith("staticDir:")) {
				Map<String, String> map = staticRoutes.get(httpMethod);

				if (map == null) {
					map = Maps.newLinkedHashMap();
				}

				try {
					map.put(path, Context.PATH_DELIMITER + splits[2].substring("staticDir:".length()));
				} catch (Exception ignored) {
				}

				staticRoutes.put(httpMethod, map);
			}
		}

		if (logger.isDebugEnabled()) {
			for (Entry<Method.HttpMethod, Map<String, HttpResponseStatus>> entry : statusRoutes.entrySet()) {
				for (Entry<String, HttpResponseStatus> pathEntry : entry.getValue().entrySet()) {
					logger.debug("[status] {} {} => {}", entry.getKey(), pathEntry.getKey(), pathEntry.getValue().getCode());
				}
			}

			for (Entry<Method.HttpMethod, Map<String, String>> entry : staticRoutes.entrySet()) {
				for (Entry<String, String> pathEntry : entry.getValue().entrySet()) {
					logger.debug("[static] {} {} => {}", entry.getKey(), pathEntry.getKey(), pathEntry.getValue());
				}
			}
		}
	}
}
