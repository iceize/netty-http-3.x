package ice.http.server.param;

public class ValidationException extends RuntimeException {
	public ValidationException(String parameterName) {
		super(parameterName + " is not valid");
	}

	public ValidationException(String parameterName, Throwable cause) {
		super(parameterName + " is not valid", cause);
	}
}
