package ice.http.server.view;

import ice.http.server.Request;
import ice.http.server.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Raw implements View {
	private final Logger logger = LoggerFactory.getLogger(Raw.class);

	@Override
	public void apply(Object result, Request request, Response response) {
		if (result == null || !(result instanceof Response)) {
			return;
		}

		try {
			Response res = (Response) result;
			response.status = res.status;

			if (res.encoding != null) {
				response.encoding = res.encoding;
			}

			if (res.contentType != null) {
				response.contentType = res.contentType;
			}

			if (res.output != null) {
				response.output = res.output;
			}

			if (!res.cookies.isEmpty()) {
				response.cookies.putAll(res.cookies);
			}

			if (!res.headers.isEmpty()) {
				response.headers.putAll(res.headers);
			}
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		}
	}
}
