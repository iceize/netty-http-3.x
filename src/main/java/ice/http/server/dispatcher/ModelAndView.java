package ice.http.server.dispatcher;

import ice.http.server.view.View;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class ModelAndView {
	public final Object model;
	public final Class<? extends View> view;

	public ModelAndView(Object model, Class<? extends View> view) {
		this.model = model;
		this.view = view;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
