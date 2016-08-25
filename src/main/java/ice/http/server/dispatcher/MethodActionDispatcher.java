package ice.http.server.dispatcher;

import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.action.Action;
import ice.http.server.action.MethodAction;
import ice.http.server.exception.InvalidViewException;
import ice.http.server.view.View;
import org.jboss.netty.channel.ChannelHandlerContext;

import java.util.Map.Entry;

public abstract class MethodActionDispatcher implements ActionDispatcher {
	protected abstract Entry<Object, View> dispatch(Request request, Response response, MethodAction methodAction);

	@Override
	public void dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		MethodAction methodAction = (MethodAction) action;
		request.actionMethod = methodAction.method();
		request.actionClass = methodAction.bean().getClass();

		Entry<Object, View> modelAndView = dispatch(request, response, methodAction);

		if (modelAndView == null) {
			throw new InvalidViewException(request.path);
		}

		Object result = modelAndView.getKey();
		View view = modelAndView.getValue();

		if (view == null) {
			throw new InvalidViewException(request.path);
		}

		view.apply(result, request, response);
	}
}
