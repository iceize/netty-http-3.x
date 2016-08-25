package ice.http.server;

import com.google.common.collect.Lists;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import java.util.List;

public class Header {
	public final String name;
	public final List<String> values;

	public Header(String name, String value) {
		this(name, Lists.newArrayList(value));
	}

	public Header(String name, List<String> values) {
		this.name = name;
		this.values = values;
	}

	public String value() {
		return values.iterator().next();
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
