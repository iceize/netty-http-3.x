package ice.http.server.binder;

import ice.http.server.Parameter;
import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.parser.Parser;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.Map;

@Bind(File.class)
public class FileBinder implements Binder {
	static Object DEFAULT_VALUE = null;

	@SuppressWarnings("unchecked")
	@Override
	public Object bind(Request request, Response response, Parameter parameter, Object value) {
		Object defaultValue = defaultValue(request, response, parameter);
		Map<String, File> files = (Map<String, File>) request.args.get(Parser.UPLOAD);

		if (CollectionUtils.isEmpty(files)) {
			return defaultValue;
		}

		File file = files.get(parameter.paramName);
		return file == null ? defaultValue : file;
	}

	@Override
	public Object defaultValue(Request request, Response response, Parameter parameter) {
		return DEFAULT_VALUE;
	}
}
