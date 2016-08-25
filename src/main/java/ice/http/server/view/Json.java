package ice.http.server.view;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ice.http.server.Request;
import ice.http.server.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Json implements View {
	private final Logger logger = LoggerFactory.getLogger(Json.class);
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Override
	public void apply(Object result, Request request, Response response) {
		response.contentType = "application/json";

		if (result != null) {
			try {
				response.output = MAPPER.writeValueAsBytes(result);
			} catch (Exception e) {
				logger.debug(e.getMessage(), e);
			}
		}
	}
}
