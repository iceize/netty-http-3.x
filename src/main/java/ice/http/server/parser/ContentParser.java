package ice.http.server.parser;

import com.google.common.collect.Maps;
import ice.http.server.utils.ClassPathScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.Set;

public class ContentParser implements InitializingBean, ApplicationContextAware {
	private ApplicationContext applicationContext;
	private final Parser dummyParser = new DummyParser();
	private final Map<String, Class<? extends Parser>> parsers = Maps.newHashMap();

	public Parser getParser(String contentType) {
		Class<? extends Parser> clazz = parsers.get(contentType);

		if (clazz == null) {
			return dummyParser;
		}

		Parser parser;

		try {
			parser = clazz.newInstance();
		} catch (Exception e) {
			return dummyParser;
		}

		if (parser instanceof ApplicationContextAware) {
			((ApplicationContextAware) parser).setApplicationContext(applicationContext);
		}

		if (parser instanceof InitializingBean) {
			try {
				((InitializingBean) parser).afterPropertiesSet();
			} catch (Exception ignored) {
			}
		}

		return parser;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Set<Class<? extends Parser>> parserClasses = ClassPathScanner.scan(Parser.class.getPackage().getName(), new ClassPathScanner.AnnotationFilter(ContentType.class));

		for (Class<? extends Parser> parserClass : parserClasses) {
			if (!Parser.class.isAssignableFrom(parserClass)) {
				continue;
			}

			ContentType contentType = parserClass.getAnnotation(ContentType.class);

			for (String type : contentType.value()) {
				parsers.put(type, parserClass);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
