package ice.http.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.annotations.Method.HttpMethod;
import ice.http.server.view.View;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Request {
	public String host;
	public Integer port;
	public String path;
	public String queryString;
	public String remoteAddress;
	public String contentType;
	public String encoding = Context.DEFAULT_ENCODING;
	public List<String> acceptLanguages;
	public boolean keepAlive;
	public View view;
	public HttpMethod method;
	public Method actionMethod;
	public Class<?> actionClass;
	public RequestSession requestSession;
	public RequestBody body; // application/json 인 경우, BODY 값에 채워넣음.
	public Throwable exception;

	public final Map<String, List<String>> params = Maps.newHashMap();
	public final Map<String, Header> headers = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);
	public final Map<String, Cookie> cookies = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);
	public final Map<String, Object> args = Maps.newHashMap();

	public static final ThreadLocal<Request> REQUEST_HOLDER = new ThreadLocal<>();
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	public static Request create(HttpRequest httpRequest, MessageEvent messageEvent) {
		Request request = new Request();

		// header
		for (String headerName : httpRequest.headers().names()) {
			String lowerHeaderName = StringUtils.lowerCase(headerName);
			request.headers.put(lowerHeaderName, new Header(lowerHeaderName, httpRequest.headers().getAll(headerName)));
		}

		// cookie
		String cookieValue = httpRequest.headers().get(HttpHeaders.Names.COOKIE);

		if (cookieValue != null) {
			Set<Cookie> cookies = Cookie.decode(cookieValue);

			if (!CollectionUtils.isEmpty(cookies)) {
				for (Cookie cookie : cookies) {
					request.cookies.put(cookie.name, cookie);
				}
			}
		}

		String host = request.header(HttpHeaders.Names.HOST);

		// host, port
		if (host != null) {
			if (host.contains(":")) {
				int index = host.indexOf(":");
				request.host = host.substring(0, index);
				request.port = Integer.parseInt(host.substring(index + 1));
			} else {
				request.host = host;
			}
		}

		if (request.port == null) {
			request.port = 80; // temp
		}

		// path, querystring, params
		String uri = httpRequest.getUri();
		QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
		request.path = queryStringDecoder.getPath();

		if (uri.contains("?")) {
			request.queryString = uri.substring(request.path.length() + 1);
			request.params.putAll(queryStringDecoder.getParameters());
		}

		// method
		request.method = HttpMethod.valueOf(httpRequest.getMethod().getName());
		String methodOverride = request.header("X-Http-Method-Override");

		if (methodOverride != null) {
			request.method = HttpMethod.valueOf(methodOverride.intern());
		}

		// remote address
		request.remoteAddress = ((InetSocketAddress) messageEvent.getRemoteAddress()).getAddress().getHostAddress();
		String forwardedFor = request.header("X-Forwarded-For");

		if (forwardedFor != null) {
			request.remoteAddress = forwardedFor.intern();
		}

		String xDaumIp = request.header("X-Daum-IP");

		if (xDaumIp != null) {
			request.remoteAddress = xDaumIp.intern();
		}

		// content type, encoding
		String contentType = request.header(HttpHeaders.Names.CONTENT_TYPE);

		if (contentType == null) {
			request.contentType = "text/html";
		} else {
			String[] contentTypeParts = contentType.split(";");
			request.contentType = contentTypeParts[0].trim().toLowerCase();

			if (contentTypeParts.length >= 2) {
				String[] encodingParts = contentTypeParts[1].split(("="));

				if (encodingParts.length == 2 && encodingParts[0].trim().equalsIgnoreCase("charset")) {
					String encoding = encodingParts[1].trim();

					if (StringUtils.isNotBlank(encoding) && ((encoding.startsWith("\"") && encoding.endsWith("\"")) || (encoding.startsWith("'") && encoding.endsWith("'")))) {
						encoding = encoding.substring(1, encoding.length() - 1).trim();
					}

					request.encoding = encoding;
				}
			}
		}

		request.keepAlive = HttpHeaders.isKeepAlive(httpRequest);

		// accept languages
		if (request.header("Accept-Language") != null) {
			final Pattern qPattern = Pattern.compile("q=([0-9\\.]+)");
			String acceptLanguage = request.header("Accept-Language").intern();
			List<String> languages = Arrays.asList(acceptLanguage.split(","));

			Collections.sort(languages, (lang1, lang2) -> {
				double q1 = 1.0;
				double q2 = 1.0;

				Matcher m1 = qPattern.matcher(lang1);
				Matcher m2 = qPattern.matcher(lang2);

				if (m1.find()) {
					q1 = Double.parseDouble(m1.group(1));
				}

				if (m2.find()) {
					q2 = Double.parseDouble(m2.group(1));
				}

				return (int) (q2 - q1);
			});

			request.acceptLanguages = Lists.newArrayList();

			for (String lang : languages) {
				request.acceptLanguages.add(lang.trim().split(";")[0]);
			}
		}

		return request;
	}

	public Map<String, String> param() {
		Map<String, String> param = Maps.newHashMap();

		for (Entry<String, List<String>> entry : params.entrySet()) {
			param.put(entry.getKey(), entry.getValue().iterator().next());
		}

		return param;
	}

	public String param(String key) {
		List<String> values = params.get(key);
		return CollectionUtils.isEmpty(values) ? null : values.iterator().next();
	}

	@SuppressWarnings("unchecked")
	public <T> T param(String key, Class<T> clazz) {
		if (clazz.isPrimitive() || clazz.isArray()) {
			throw new IllegalArgumentException("clazz should not be primitive type");
		}

		List<String> values = params.get(key);

		if (CollectionUtils.isEmpty(values)) {
			return null;
		}

		String value = values.iterator().next();

		if (clazz == Boolean.class) {
			return (T) Boolean.valueOf(value);
		}

		if (clazz == Byte.class) {
			return (T) Byte.valueOf(value);
		}

		if (clazz == Character.class) {
			return (T) Character.valueOf(value.charAt(0));
		}

		if (clazz == Short.class) {
			return (T) Short.valueOf(value);
		}

		if (clazz == Integer.class) {
			return (T) Integer.valueOf(value);
		}

		if (clazz == Long.class) {
			return (T) Long.valueOf(value);
		}

		if (clazz == Float.class) {
			return (T) Float.valueOf(value);
		}

		if (clazz == Double.class) {
			return (T) Double.valueOf(value);
		}

		if (clazz == String.class) {
			return (T) value;
		}

		throw new UnsupportedOperationException("clazz is not supported");
	}

	public String header(String headerName) {
		Header header = headers.get(StringUtils.lowerCase(headerName));
		return header == null ? null : header.value();
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	public interface RequestSession { // marker interface
	}

	public static class RequestBody {
		public final String body;

		public RequestBody(String body) {
			this.body = body;
		}

		public <T> T to(Class<T> clazz) {
			try {
				return body == null ? null : MAPPER.readValue(body, clazz);
			} catch (IOException e) {
				return null;
			}
		}

		public <T> T to(TypeReference<T> typeReference) {
			try {
				return body == null ? null : MAPPER.readValue(body, typeReference);
			} catch (IOException e) {
				return null;
			}
		}

		public JsonNode to() {
			try {
				return body == null ? null : MAPPER.readTree(body);
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public String toString() {
			return body;
		}
	}
}
