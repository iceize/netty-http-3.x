package ice.http.server.action;

public class StaticAction implements Action {
	private final String path;
	private final StaticContent staticContent;

	public StaticAction(String path, StaticContent staticContent) {
		this.path = path;
		this.staticContent = staticContent;
	}

	public String path() {
		return path;
	}

	public byte[] contents() {
		return staticContent == null ? null : staticContent.contents;
	}

	public long timestamp() {
		return staticContent == null ? System.currentTimeMillis() : staticContent.timestamp;
	}

	public static class StaticContent {
		private final byte[] contents;
		private final long timestamp;

		public StaticContent(byte[] contents, long timestamp) {
			this.contents = contents;
			this.timestamp = timestamp;
		}
	}
}
