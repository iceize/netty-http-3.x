package ice.http.server.param;

import ice.http.server.Parameter;
import ice.http.server.Request;

public interface Converter {
	Object convert(Request request, Parameter parameter, String value);
}
