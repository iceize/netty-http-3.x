package ice.http.server;

import ice.http.server.view.Json;
import ice.http.server.view.View;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class Settings {
	private final String name;
	private final int port;

	// server
	private int threadCount = 0;

	// http
	private int keepAliveTimeout = -1;
	private boolean cache = false;
	private int cacheTtl = 24 * 60 * 60;

	// websocket
	private int webSocketMaxContentLength = 65345;

	// view, converter, validator
	private String extensions;
	private Class<? extends View> defaultView = Json.class;

	// socket
	private boolean keepAlive = true;
	private boolean reuseAddress = true;
	private boolean useTcpNoDelay = true;
	private int backlog = 1024;
	private int soLinger = -1;
	private int receiveBufferSize = 262140;
	private int connectTimeoutMillis = 10000;

	public Settings(String name, int port) {
		this.name = name;
		this.port = port;
	}

	public String getName() {
		return name;
	}

	public int getPort() {
		return port;
	}

	public int getThreadCount() {
		return threadCount;
	}

	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	public int getKeepAliveTimeout() {
		return keepAliveTimeout;
	}

	public void setKeepAliveTimeout(int keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}

	public boolean isCache() {
		return cache;
	}

	public void setCache(boolean cache) {
		this.cache = cache;
	}

	public int getCacheTtl() {
		return cacheTtl;
	}

	public void setCacheTtl(int cacheTtl) {
		this.cacheTtl = cacheTtl;
	}

	public int getWebSocketMaxContentLength() {
		return webSocketMaxContentLength;
	}

	public void setWebSocketMaxContentLength(int webSocketMaxContentLength) {
		this.webSocketMaxContentLength = webSocketMaxContentLength;
	}

	public String getExtensions() {
		return extensions;
	}

	public void setExtensions(String extensions) {
		this.extensions = extensions;
	}

	public Class<? extends View> getDefaultView() {
		return defaultView;
	}

	public void setDefaultView(Class<? extends View> defaultView) {
		this.defaultView = defaultView;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	public boolean isReuseAddress() {
		return reuseAddress;
	}

	public void setReuseAddress(boolean reuseAddress) {
		this.reuseAddress = reuseAddress;
	}

	public boolean isUseTcpNoDelay() {
		return useTcpNoDelay;
	}

	public void setUseTcpNoDelay(boolean useTcpNoDelay) {
		this.useTcpNoDelay = useTcpNoDelay;
	}

	public int getBacklog() {
		return backlog;
	}

	public void setBacklog(int backlog) {
		this.backlog = backlog;
	}

	public int getSoLinger() {
		return soLinger;
	}

	public void setSoLinger(int soLinger) {
		this.soLinger = soLinger;
	}

	public int getReceiveBufferSize() {
		return receiveBufferSize;
	}

	public void setReceiveBufferSize(int receiveBufferSize) {
		this.receiveBufferSize = receiveBufferSize;
	}

	public int getConnectTimeoutMillis() {
		return connectTimeoutMillis;
	}

	public void setConnectTimeoutMillis(int connectTimeoutMillis) {
		this.connectTimeoutMillis = connectTimeoutMillis;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
