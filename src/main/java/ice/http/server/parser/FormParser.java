package ice.http.server.parser;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.Request;
import ice.http.server.exception.ContentParseException;
import org.apache.commons.lang.RandomStringUtils;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.multipart.*;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@ContentType({"application/x-www-form-urlencoded", "multipart/form-data"})
public class FormParser implements Parser {
	private static final HttpDataFactory HTTP_DATA_FACTORY = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

	static {
		DiskFileUpload.deleteOnExitTemporaryFile = true;
		DiskFileUpload.baseDirectory = null;
		DiskAttribute.deleteOnExitTemporaryFile = true;
		DiskAttribute.baseDirectory = null;
	}

	private final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
	private HttpPostRequestDecoder decoder;

	@Override
	public void init(HttpRequest httpRequest) {
		try {
			decoder = new HttpPostRequestDecoder(HTTP_DATA_FACTORY, httpRequest);
		} catch (ErrorDataDecoderException e) {
			throw new ContentParseException(e);
		} catch (Exception ignored) {
		}
	}

	@Override
	public void offer(HttpChunk httpChunk) {
		try {
			decoder.offer(httpChunk);
		} catch (ErrorDataDecoderException e) {
			throw new ContentParseException(e);
		}
	}

	@Override
	public void close() {
		if (decoder != null) {
			decoder.cleanFiles();
		}
	}

	private void params(Map<String, List<String>> params, String key, String value) {
		List<String> values = params.get(key);

		if (values == null) {
			values = Lists.newArrayList();
		}

		values.add(value);
		params.put(key, values);
	}

	@Override
	public void parse(Request request) {
		List<InterfaceHttpData> interfaceHttpDatas;

		try {
			interfaceHttpDatas = decoder.getBodyHttpDatas();
		} catch (NotEnoughDataDecoderException e) {
			throw new ContentParseException(e);
		}

		Map<String, List<String>> params = Maps.newHashMap();
		Map<String, File> files = Maps.newHashMap();

		for (InterfaceHttpData interfaceHttpData : interfaceHttpDatas) {
			if (interfaceHttpData.getHttpDataType() == HttpDataType.Attribute) {
				Attribute attribute = (Attribute) interfaceHttpData;

				try {
					params(params, attribute.getName(), attribute.getValue());
				} catch (IOException ignored) {
				}
			} else if (interfaceHttpData.getHttpDataType() == HttpDataType.FileUpload) {
				FileUpload fileUpload = (FileUpload) interfaceHttpData;

				if (fileUpload.isCompleted() && fileUpload.length() > 0L) {
					String random = RandomStringUtils.randomAlphanumeric(32);
					File target = new File(tmpDir, random + fileUpload.getFilename());

					try {
						fileUpload.renameTo(target);
					} catch (IOException ignored) {
					}

					files.put(fileUpload.getName(), target);
					target.deleteOnExit();
				}
			}
		}

		if (!CollectionUtils.isEmpty(params)) {
			request.params.putAll(params);
		}

		if (!CollectionUtils.isEmpty(files)) {
			request.args.put(UPLOAD, files);
		}
	}
}
