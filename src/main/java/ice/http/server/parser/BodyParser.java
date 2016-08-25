package ice.http.server.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.Context;
import ice.http.server.Request;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.springframework.util.CollectionUtils;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@ContentType("application/json")
public class BodyParser implements Parser {
	private Charset charset;
	private final StringBuffer content = new StringBuffer();
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public void init(HttpRequest httpRequest) {
		charset = Context.DEFAULT_CHARSET;
		content.append(httpRequest.getContent().toString(charset));
	}

	@Override
	public void offer(HttpChunk httpChunk) {
		content.append(httpChunk.getContent().toString(charset));
	}

	@Override
	public void close() {
	}

	private void params(Map<String, List<String>> params, String key, JsonNode jsonNode) {
		if (!jsonNode.isValueNode() || (jsonNode instanceof NullNode)) {
			return;
		}

		String value = jsonNode.asText();

		if (value != null) {
			List<String> values = params.get(key);

			if (values == null) {
				values = Lists.newArrayList();
			}

			values.add(value);
			params.put(key, values);
		}
	}

	@Override
	public void parse(Request request) {
		String body = content.toString();

		if (StringUtils.isEmpty(body)) {
			return;
		}

		request.args.put(BODY, body);
		request.body = new Request.RequestBody(body);

		JsonNode rootNode;

		try {
			rootNode = MAPPER.readTree(body);
		} catch (Exception e) {
			return;
		}

		Map<String, List<String>> params = Maps.newHashMap();
		Iterator<Entry<String, JsonNode>> fields = rootNode.fields();

		while (fields.hasNext()) {
			Entry<String, JsonNode> field = fields.next();
			JsonNode jsonNode = field.getValue();

			if (jsonNode.isArray()) {
				Iterator<JsonNode> values = field.getValue().elements();

				while (values.hasNext()) {
					params(params, field.getKey(), values.next());
				}
			} else if (jsonNode.isValueNode()) {
				params(params, field.getKey(), jsonNode);
			}
		}

		if (!CollectionUtils.isEmpty(params)) {
			request.params.putAll(params);
		}
	}
}
