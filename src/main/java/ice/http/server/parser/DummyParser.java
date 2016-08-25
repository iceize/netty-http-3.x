package ice.http.server.parser;

import ice.http.server.Request;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class DummyParser implements Parser {
	@Override
	public void init(HttpRequest httpRequest) {
	}

	@Override
	public void offer(HttpChunk httpChunk) {
	}

	@Override
	public void close() {
	}

	@Override
	public void parse(Request request) {
	}
}
