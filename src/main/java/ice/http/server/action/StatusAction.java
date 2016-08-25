package ice.http.server.action;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

public class StatusAction implements Action {
	private final String path;
	private final HttpResponseStatus status;

	public StatusAction(String path, HttpResponseStatus status) {
		this.path = path;
		this.status = status;
	}

	public String path() {
		return path;
	}

	public HttpResponseStatus status() {
		return status;
	}
}
