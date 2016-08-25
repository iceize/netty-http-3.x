package ice.http.server.dispatcher;

import ice.http.server.*;
import ice.http.server.action.Action;
import ice.http.server.action.StaticAction;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.springframework.beans.factory.InitializingBean;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StaticActionDispatcher implements SettingsAware, ActionDispatcher, InitializingBean {
	private Settings settings;
	private boolean needCache;
	private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Class<? extends Action> assignableFrom() {
		return StaticAction.class;
	}

	private boolean needCache(Request request, StaticAction staticAction) {
		String ifModifiedSince = request.header(HttpHeaders.Names.IF_MODIFIED_SINCE);

		if (!needCache || StringUtils.isBlank(ifModifiedSince)) {
			return false;
		}

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);

		try {
			Date ifModifiedSinceDate = simpleDateFormat.parse(ifModifiedSince);
			return ifModifiedSinceDate.getTime() / 1000 == staticAction.timestamp() / 1000;
		} catch (ParseException ignored) {
		}

		return false;
	}

	@Override
	public void dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		StaticAction staticAction = (StaticAction) action;

		if (staticAction.contents() == null) {
			response.status = HttpResponseStatus.MOVED_PERMANENTLY;
			response.headers.put(HttpHeaders.Names.LOCATION, new Header(HttpHeaders.Names.LOCATION, staticAction.path() + "/"));
		}

		if (!settings.isCache()) {
			response.output = staticAction.contents();
			response.contentType = Context.getContentType(staticAction.path());
			return;
		}

		if (needCache(request, staticAction)) {
			response.status = HttpResponseStatus.NOT_MODIFIED;
			response.header(HttpHeaders.Names.DATE, FastDateFormat.getInstance(HTTP_DATE_FORMAT, TimeZone.getTimeZone("GMT"), Locale.US).format(Calendar.getInstance()));
			return;
		}

		response.output = staticAction.contents();
		response.contentType = Context.getContentType(staticAction.path());

		Calendar calendar = Calendar.getInstance();
		FastDateFormat dateFormat = FastDateFormat.getInstance(HTTP_DATE_FORMAT, TimeZone.getTimeZone("GMT"), Locale.US);
		response.header(HttpHeaders.Names.DATE, dateFormat.format(calendar));
		calendar.add(Calendar.SECOND, settings.getCacheTtl());
		response.header(HttpHeaders.Names.EXPIRES, dateFormat.format(calendar));
		response.header(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + settings.getCacheTtl());
		response.header(HttpHeaders.Names.LAST_MODIFIED, dateFormat.format(staticAction.timestamp()));
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		try {
			this.needCache = Boolean.valueOf(System.getProperty("server.static.cache", "true"));
		} catch (Exception e) {
			this.needCache = true;
		}
	}
}
