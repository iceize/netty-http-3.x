package ice.http.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.CountDownLatch;

public class ServerSignalHandler implements SignalHandler {
	private final Server[] servers;
	private final CountDownLatch countDownLatch;
	private SignalHandler signalHandler;
	private final Logger logger = LoggerFactory.getLogger(ServerSignalHandler.class);

	private ServerSignalHandler(CountDownLatch countDownLatch, Server[] servers) {
		this.countDownLatch = countDownLatch;
		this.servers = servers;
	}

	public static ServerSignalHandler install(String signalName, CountDownLatch countDownLatch, Server... servers) {
		Signal signal = new Signal(signalName);
		ServerSignalHandler handler = new ServerSignalHandler(countDownLatch, servers);
		handler.signalHandler = Signal.handle(signal, handler);
		return handler;
	}

	@Override
	public void handle(Signal signal) {
		try {
			for (Server server : servers) {
				if (server != null) {
					server.stop();
					logger.info(server.getClass().getSimpleName() + " is stopped...");

					while (server.running()) {
						try {
							Thread.sleep(100L);
						} catch (InterruptedException e) { // ignore
						}
					}
				}
			}
		} finally {
			countDownLatch.countDown();

			if (signalHandler != SIG_DFL && signalHandler != SIG_IGN) {
				signalHandler.handle(signal);
			}
		}
	}
}
