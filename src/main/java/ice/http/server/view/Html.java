package ice.http.server.view;

import com.google.common.collect.Maps;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.*;
import ice.http.server.Context;
import ice.http.server.Request;
import ice.http.server.Response;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

public class Html implements View, InitializingBean {
	protected Configuration configuration;
	protected final Map<String, Object> staticModels = Maps.newHashMap();
	private final Logger logger = LoggerFactory.getLogger(Html.class);

	@Override
	public void apply(Object result, Request request, Response response) {
		response.contentType = "text/html";

		if (result == null) {
			return;
		}

		try {
			if (result instanceof Page) {
				Page page = (Page) result;
				String templateName = page.getTemplate();
				Map<String, Object> model = page.getModel();

				if (model == null) {
					model = Maps.newHashMap();
				}

				model.putAll(staticModels);
				Template template = getTemplate(configuration, templateName, null, null);

				if (template != null) {
					try {
						StringWriter stringWriter = new StringWriter();
						template.process(model, stringWriter);
						response.output = stringWriter.getBuffer().toString().getBytes();
						return;
					} catch (Exception except) {
						throw new IllegalStateException(except);
					}
				}

				throw new IllegalStateException(String.format("template(%s) does not exist!", templateName));
			}

			response.output = result.toString().getBytes();
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		}
	}

	private Template getTemplate(Configuration configuration, String templateName, Locale locale, String encoding) {
		Template template = null;

		try {
			if (locale == null) {
				locale = Locale.getDefault();
			}

			encoding = StringUtils.isEmpty(encoding) ? configuration.getDefaultEncoding() : encoding;
			template = configuration.getTemplate(templateName, locale, encoding);
		} catch (Exception except) {
			logger.error("fail in creating content using template(" + templateName + ") : " + except.getMessage());
		}

		return template;
	}

	protected TemplateLoader getTemplateLoader() {
		return new ClassTemplateLoader(ClassTemplateLoader.class, "/META-INF/template");
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Class<?>[] modelClasses = new Class<?>[]{};

		Version version = Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS;
		DefaultObjectWrapperBuilder defaultObjectWrapperBuilder = new DefaultObjectWrapperBuilder(version);
		defaultObjectWrapperBuilder.setExposeFields(true);
		DefaultObjectWrapper defaultObjectWrapper = defaultObjectWrapperBuilder.build();

		for (Class<?> modelClass : modelClasses) {
			try {
				TemplateHashModel templateHashModel = (TemplateHashModel) defaultObjectWrapper.getStaticModels().get(modelClass.getName());
				staticModels.put(modelClass.getSimpleName(), templateHashModel);
			} catch (TemplateModelException e) {
				logger.error(e.getMessage(), e);
			}
		}

		configuration = new Configuration(version);
		configuration.setTemplateLoader(getTemplateLoader());
		configuration.setObjectWrapper(defaultObjectWrapper);
		configuration.setDefaultEncoding(Context.DEFAULT_ENCODING);

		try {
			int updateDelay = Integer.parseInt(System.getProperty("freemarker.update.delay", "0"));
			configuration.setTemplateUpdateDelayMilliseconds(updateDelay * 1000L);
			logger.debug("Freemarker Template UpdateDelay set to {}", updateDelay);
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		}

		logger.debug("Default Configuration is initiated.");
	}

	public static class Page {
		private final String template;
		private final Map<String, Object> model;

		public Page(String template) {
			this(template, null);
		}

		public Page(String template, Map<String, Object> model) {
			this.template = template;
			this.model = model;
		}

		public String getTemplate() {
			return template;
		}

		public Map<String, Object> getModel() {
			return model;
		}
	}
}
