package ice.http.server.parser;

import ice.http.server.Request;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;

public interface Parser {
	String BODY = "__BODY";
	String UPLOAD = "__UPLOAD";

	void init(HttpRequest httpRequest);

	void offer(HttpChunk httpChunk);

	void close();

	void parse(Request request);
}
