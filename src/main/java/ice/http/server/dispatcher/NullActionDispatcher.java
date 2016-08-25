package ice.http.server.dispatcher;

import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.action.Action;
import ice.http.server.action.NullAction;
import ice.http.server.exception.NotFoundException;
import org.jboss.netty.channel.ChannelHandlerContext;

public class NullActionDispatcher implements ActionDispatcher {
	@Override
	public Class<? extends Action> assignableFrom() {
		return NullAction.class;
	}

	@Override
	public void dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		throw new NotFoundException(request.path);
	}
}
