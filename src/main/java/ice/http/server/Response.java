package ice.http.server;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.http.entity.ContentType;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;

public class Response {
	public HttpResponseStatus status = HttpResponseStatus.OK;
	public String contentType;
	public String encoding = Context.DEFAULT_ENCODING;
	public String requestPath;
	public boolean shouldKeepAlive;
	public byte[] output;
	public Throwable cause;

	public final Map<String, Header> headers = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);
	public final Map<String, Cookie> cookies = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);

	public static Response create(HttpRequest httpRequest) {
		Response response = new Response();
		response.requestPath = httpRequest.getUri();
		response.contentType = StringUtils.defaultIfEmpty(httpRequest.headers().get(HttpHeaders.Names.CONTENT_TYPE), ContentType.TEXT_PLAIN.getMimeType());
		response.shouldKeepAlive = HttpHeaders.isKeepAlive(httpRequest);
		return response;
	}

	public void header(String name, String value) {
		this.headers.put(name, new Header(name, value));
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
