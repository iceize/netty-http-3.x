package ice.http.server.exception;

public class InvalidViewException extends RuntimeException {
	private static final long serialVersionUID = 3579785793926773000L;

	public InvalidViewException(String path) {
		super(path + " should declare view");
	}
}
