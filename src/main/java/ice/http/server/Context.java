package ice.http.server;

import com.google.common.collect.Maps;
import ice.http.server.annotations.Method;
import ice.http.server.utils.ClassPathScanner;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Context {
	public static final String DEFAULT_ENCODING = "UTF-8";
	public static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_ENCODING);
	public static final String PATH_DELIMITER = "/";
	public static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([0-9a-zA-Z_-]+)\\}");
	private static final Map<String, String> MIME_TYPES = Maps.newHashMap();
	private static final Map<Class<? extends Annotation>, Method.HttpMethod> MVC_ANNOTATIONS = Maps.newLinkedHashMap();
	private static final String DEFAULT_MIMETYPE = "application/octet-stream";

	static {
		Set<Class<? extends Method>> annotationClasses = ClassPathScanner.scan(Method.class.getPackage().getName(), new ClassPathScanner.AnnotationFilter(Method.class));

		for (Class<? extends Method> annotationClass : annotationClasses) {
			Method method = annotationClass.getAnnotation(Method.class);
			MVC_ANNOTATIONS.put(annotationClass, method.value());
		}

		List<String> lines = null;

		try {
			lines = IOUtils.readLines(Context.class.getResourceAsStream("/META-INF/netty-http/mime.types"), DEFAULT_CHARSET);
		} catch (IOException ignored) {
		}

		if (!CollectionUtils.isEmpty(lines)) {
			for (String line : lines) {
				if (line == null || "".equals(line) || line.startsWith("#")) {
					continue;
				}

				String[] splits = StringUtils.split(line);

				if (splits.length == 1) {
					continue;
				}

				String contentType = splits[0];

				for (int i = 1; i < splits.length; i++) {
					MIME_TYPES.put(splits[i], contentType);
				}
			}
		}
	}

	public static String getContentType(String filename) {
		String extension = FilenameUtils.getExtension(filename);

		if (StringUtils.isEmpty(extension)) {
			return DEFAULT_MIMETYPE;
		}

		return MIME_TYPES.containsKey(extension) ? MIME_TYPES.get(extension) : DEFAULT_MIMETYPE;
	}

	public static Map<Class<? extends Annotation>, Method.HttpMethod> getMvcAnnotations() {
		return Collections.unmodifiableMap(MVC_ANNOTATIONS);
	}
}
