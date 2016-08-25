package ice.http.server.dispatcher;

import ice.http.server.Request;
import ice.http.server.Response;
import ice.http.server.action.Action;
import org.jboss.netty.channel.ChannelHandlerContext;

public interface ActionDispatcher {
	Class<? extends Action> assignableFrom();

	void dispatch(ChannelHandlerContext context, Action action, Request request, Response response);
}
