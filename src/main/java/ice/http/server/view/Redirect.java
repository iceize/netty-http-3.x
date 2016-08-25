package ice.http.server.view;

import ice.http.server.Header;
import ice.http.server.Request;
import ice.http.server.Response;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

public class Redirect implements View {
	@Override
	public void apply(Object result, Request request, Response response) {
		String url = result.toString();

		if (!StringUtils.startsWith(url, "http")) {
			url = "http://" + request.host + ":" + request.port + url;
		}

		response.status = HttpResponseStatus.MOVED_PERMANENTLY;
		response.headers.put(HttpHeaders.Names.LOCATION, new Header(HttpHeaders.Names.LOCATION, url));
	}
}
