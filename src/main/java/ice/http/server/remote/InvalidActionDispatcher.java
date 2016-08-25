package ice.http.server.remote;

import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.action.Action;
import ice.http.server.dispatcher.ActionDispatcher;
import org.jboss.netty.channel.ChannelHandlerContext;

public class InvalidActionDispatcher implements ActionDispatcher {
	@Override
	public Class<? extends Action> assignableFrom() {
		return InvalidAction.class;
	}

	@Override
	public void dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		throw new IllegalArgumentException(request.path);
	}
}
