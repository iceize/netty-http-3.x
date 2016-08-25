package ice.http.server.remote;

import com.fasterxml.jackson.core.type.TypeReference;

import java.lang.reflect.Type;

public class RemoteTypeReference<T> extends TypeReference<T> {
	private final Type type;

	public RemoteTypeReference(Type type) {
		this.type = type;
	}

	@Override
	public Type getType() {
		return type;
	}
}
