package ice.http.server.view;

import ice.http.server.Request;
import ice.http.server.Response;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public abstract class Image implements View {
	protected abstract String extension();
	private final Logger logger = LoggerFactory.getLogger(Image.class);

	@Override
	public void apply(Object result, Request request, Response response) {
		if (result == null || !(result instanceof BufferedImage)) {
			return;
		}

		ByteArrayOutputStream outputStream = null;

		try {
			outputStream = new ByteArrayOutputStream();
			ImageIO.write((BufferedImage) result, extension(), outputStream);
			response.contentType = "image/" + extension();
			response.output = outputStream.toByteArray();
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		} finally {
			IOUtils.closeQuietly(outputStream);
		}
	}
}
