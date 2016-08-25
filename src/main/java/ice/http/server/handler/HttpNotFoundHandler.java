package ice.http.server.handler;

import ice.http.server.Response;

public interface HttpNotFoundHandler {
	Response handleNotFound(String path, String contentType);
}
