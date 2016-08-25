package ice.http.server.dispatcher;

import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.action.Action;
import ice.http.server.action.StatusAction;
import org.jboss.netty.channel.ChannelHandlerContext;

public class StatusActionDispatcher implements ActionDispatcher {
	@Override
	public Class<? extends Action> assignableFrom() {
		return StatusAction.class;
	}

	@Override
	public void dispatch(ChannelHandlerContext context, Action action, Request request, Response response) {
		response.status = ((StatusAction) action).status();
	}
}
