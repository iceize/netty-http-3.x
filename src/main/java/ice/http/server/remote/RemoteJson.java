package ice.http.server.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;

public class RemoteJson implements View {
	private final Gson gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC).create();
	private final Logger logger = LoggerFactory.getLogger(RemoteJson.class);

	@Override
	public void apply(Object result, Request request, Response response) {
		response.contentType = "application/json";

		if (result != null) {
			try {
				response.output = gson.toJson(result).getBytes();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}
}
