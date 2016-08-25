package ice.http.server.exception;

public class NotFoundException extends RuntimeException {
	private static final long serialVersionUID = 3579785793926773000L;

	public NotFoundException(String path) {
		super(path + " is not found");
	}
}
