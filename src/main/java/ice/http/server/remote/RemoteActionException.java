package ice.http.server.remote;

import ice.http.server.Request;

public class RemoteActionException extends RuntimeException {
	private final Request request;

	public RemoteActionException(Request request, Throwable cause) {
		super(cause);
		this.request = request;
	}

	public Request getRequest() {
		return request;
	}
}
