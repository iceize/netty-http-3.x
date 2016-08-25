package ice.http.server;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jboss.netty.handler.codec.http.HttpConstants;
import org.jboss.netty.handler.codec.http.HttpHeaderDateFormat;
import org.jboss.netty.handler.codec.http.cookie.CookieHeaderNames;

import java.nio.CharBuffer;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

public class Cookie implements Comparable<Cookie> {
	public final String name;
	public final String value;
	public final String domain;
	public final String path;
	public final int maxAge;

	public Cookie(String name, String value) {
		this(name, value, null, null);
	}

	public Cookie(String name, String value, String domain, String path) {
		this(name, value, domain, path, Integer.MIN_VALUE);
	}

	public Cookie(String name, String value, String domain, String path, int maxAge) {
		this.name = name;
		this.value = value;
		this.domain = domain;
		this.path = path;
		this.maxAge = maxAge;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	public String encode() {
		StringBuilder sb = new StringBuilder();

		add(sb, name, value == null ? "" : value);

		if (maxAge != Integer.MIN_VALUE) {
			add(sb, CookieHeaderNames.MAX_AGE, maxAge);
			Date expires = new Date((long) maxAge * 1000 + System.currentTimeMillis());
			add(sb, CookieHeaderNames.EXPIRES, HttpHeaderDateFormat.get().format(expires));
		}

		if (path != null) {
			add(sb, CookieHeaderNames.PATH, path);
		}

		if (domain != null) {
			add(sb, CookieHeaderNames.DOMAIN, domain);
		}

		if (sb.length() > 0) {
			sb.setLength(sb.length() - 2);
		}

		return sb.toString();
	}

	private void add(StringBuilder sb, String name, Object value) {
		sb.append(name).append((char) HttpConstants.EQUALS).append(value).append((char) HttpConstants.SEMICOLON).append((char) HttpConstants.SP);
	}

	private static final String RFC2965_VERSION = "$Version";
	private static final String RFC2965_PATH = "$" + CookieHeaderNames.PATH;
	private static final String RFC2965_DOMAIN = "$" + CookieHeaderNames.DOMAIN;
	private static final String RFC2965_PORT = "$Port";

	public static Set<Cookie> decode(String header) {
		if (header == null) {
			throw new NullPointerException("header");
		}

		final int headerLen = header.length();

		if (headerLen == 0) {
			return Collections.emptySet();
		}

		int i = 0;
		boolean rfc2965Style = false;
		Set<Cookie> cookies = new TreeSet<>();

		if (header.regionMatches(true, 0, RFC2965_VERSION, 0, RFC2965_VERSION.length())) {
			// RFC 2965 style cookie, move to after version value
			i = header.indexOf(';') + 1;
			rfc2965Style = true;
		}

		loop: for (;;) {
			// Skip spaces and separators.
			for (;;) {
				if (i == headerLen) {
					break loop;
				}

				char c = header.charAt(i);

				if (c == '\t' || c == '\n' || c == 0x0b || c == '\f' || c == '\r' || c == ' ' || c == ',' || c == ';') {
					i++;
					continue;
				}

				break;
			}

			int nameBegin = i;
			int nameEnd = i;
			int valueBegin = -1;
			int valueEnd = -1;

			if (i != headerLen) {
				keyValLoop: for (;;) {
					char curChar = header.charAt(i);

					if (curChar == ';') {
						// NAME; (no value till ';')
						nameEnd = i;
						valueBegin = valueEnd = -1;
						break keyValLoop;
					} else if (curChar == '=') {
						// NAME=VALUE
						nameEnd = i;
						i++;

						if (i == headerLen) {
							// NAME= (empty value, i.e. nothing after '=')
							valueBegin = valueEnd = 0;
							break keyValLoop;
						}

						valueBegin = i;
						// NAME=VALUE;
						int semiPos = header.indexOf(';', i);
						valueEnd = i = semiPos > 0 ? semiPos : headerLen;
						break keyValLoop;
					} else {
						i++;
					}

					if (i == headerLen) {
						// NAME (no value till the end of string)
						nameEnd = headerLen;
						valueBegin = valueEnd = -1;
						break;
					}
				}
			}

			if (rfc2965Style && (header.regionMatches(nameBegin, RFC2965_PATH, 0, RFC2965_PATH.length()) ||
					header.regionMatches(nameBegin, RFC2965_DOMAIN, 0, RFC2965_DOMAIN.length()) ||
					header.regionMatches(nameBegin, RFC2965_PORT, 0, RFC2965_PORT.length()))) {
				// skip obsolete RFC2965 fields
				continue;
			}

			Cookie cookie = initCookie(header, nameBegin, nameEnd, valueBegin, valueEnd);

			if (cookie != null) {
				cookies.add(cookie);
			}
		}

		return cookies;
	}

	private static Cookie initCookie(String header, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
		// Skipping cookie with null name
		if (nameBegin == -1 || nameBegin == nameEnd) {
			return null;
		}

		// Skipping cookie with null value
		if (valueBegin == -1) {
			return null;
		}

		CharSequence wrappedValue = CharBuffer.wrap(header, valueBegin, valueEnd);
		CharSequence unwrappedValue = unwrapValue(wrappedValue);

		// Skipping cookie because starting quotes are not properly balanced in wrappedValue
		if (unwrappedValue == null) {
			return null;
		}

		final String name = header.substring(nameBegin, nameEnd);
		return new Cookie(name, unwrappedValue.toString());
	}

	private static CharSequence unwrapValue(CharSequence charSequence) {
		final int len = charSequence.length();

		if (len > 0 && charSequence.charAt(0) == '"') {
			if (len >= 2 && charSequence.charAt(len - 1) == '"') {
				// properly balanced
				return len == 2 ? "" : charSequence.subSequence(1, len - 1);
			}

			return null;
		}

		return charSequence;
	}

	@Override
	public int compareTo(Cookie cookie) {
		int compared = name.compareToIgnoreCase(cookie.name);

		if (compared != 0) {
			return compared;
		}

		if (path == null) {
			if (cookie.path != null) {
				return -1;
			}
		} else if (cookie.path == null) {
			return 1;
		} else {
			compared = path.compareTo(cookie.path);

			if (compared != 0) {
				return compared;
			}
		}

		if (domain == null) {
			if (cookie.domain != null) {
				return -1;
			}
		} else if (cookie.domain == null) {
			return 1;
		} else {
			compared = domain.compareToIgnoreCase(cookie.domain);
			return compared;
		}

		return 0;
	}
}
