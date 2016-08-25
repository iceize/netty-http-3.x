package ice.http.server.view;

import ice.http.server.Context;
import ice.http.server.Request;
import ice.http.server.Response;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public class File implements View {
	private final Logger logger = LoggerFactory.getLogger(File.class);

	@Override
	public void apply(Object result, Request request, Response response) {
		if (result == null || !(result instanceof java.io.File)) {
			return;
		}

		InputStream inputStream = null;
		ByteArrayOutputStream outputStream = null;

		try {
			java.io.File file = (java.io.File) result;
			inputStream = new FileInputStream(file);
			outputStream = new ByteArrayOutputStream();
			IOUtils.copyLarge(inputStream, outputStream);

			response.contentType = Context.getContentType(file.getName());
			response.output = outputStream.toByteArray();
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		} finally {
			IOUtils.closeQuietly(inputStream);
			IOUtils.closeQuietly(outputStream);
		}
	}
}
