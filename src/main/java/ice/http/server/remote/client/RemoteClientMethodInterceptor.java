package ice.http.server.remote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ice.http.server.remote.RemoteHeaderNames;
import ice.http.server.remote.RemoteTypeReference;
import ice.http.server.remote.annotations.Name;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.remoting.RemoteInvocationFailureException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RemoteClientMethodInterceptor implements MethodInterceptor {
	private final HttpClient httpClient;
	private final String serviceName;
	private final String serverAddress;
	private final boolean useParameterNames;
	private final Map<Method, List<String>> methodParameters = Maps.newHashMap();
	private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	public RemoteClientMethodInterceptor(Class<?> clazz, HttpClient httpClient, String serviceName, String serverAddress, boolean useParameterNames) {
		this.httpClient = httpClient;
		this.serviceName = serviceName;
		this.serverAddress = serverAddress;
		this.useParameterNames = useParameterNames;

		for (Method method : clazz.getDeclaredMethods()) {
			if (clazz.isInterface()) { // interface
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();

				for (Annotation[] annotations : parameterAnnotations) {
					String name = null;

					for (Annotation annotation : annotations) {
						if (annotation.annotationType() == Name.class) {
							String value = (String) AnnotationUtils.getValue(annotation, "value");

							if (value != null && !"".equals(value)) {
								name = value;
								break;
							}
						}
					}

					if (name == null && useParameterNames) {
						throw new IllegalArgumentException(clazz.getName() + "#" + method.getName() + " doesn't declare @Name annotation");
					}

					List<String> names = methodParameters.get(method);

					if (names == null) {
						names = Lists.newArrayList();
					}

					names.add(name);
					methodParameters.put(method, names);
				}
			} else {
				ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
				String[] names = parameterNameDiscoverer.getParameterNames(method);
				methodParameters.put(method, Arrays.asList(names));
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		List<String> names = methodParameters.get(invocation.getMethod());
		Object[] arguments = invocation.getArguments();

		Object request;

		if (useParameterNames) {
			request = Maps.newLinkedHashMap();

			for (int i = 0; i < arguments.length; i++) {
				((Map<String, Object>) request).put(names.get(i), arguments[i]);
			}
		} else {
			request = Lists.newArrayList(arguments);
		}

		HttpPost httpPost = null;
		Type returnType = invocation.getMethod().getGenericReturnType();

		try {
			httpPost = new HttpPost(serverAddress + "/" + serviceName + "/" + invocation.getMethod().getName());
			StringEntity entity = new StringEntity(MAPPER.writeValueAsString(request), ContentType.APPLICATION_JSON);
			httpPost.setEntity(entity);

			if (useParameterNames) {
				httpPost.addHeader(RemoteHeaderNames.REMOTE_USE_PARAMETER_NAMES, "true");
			}

			HttpResponse httpResponse = httpClient.execute(httpPost);
			int status = httpResponse.getStatusLine().getStatusCode();

			if (status == HttpStatus.SC_OK) {
				if (returnType == void.class) {
					EntityUtils.consumeQuietly(httpResponse.getEntity());
					return null;
				}

				String content = EntityUtils.toString(httpResponse.getEntity());
				if (StringUtils.isEmpty(content)) {
					return null;
				}

				return MAPPER.readValue(content, new RemoteTypeReference<>(returnType));
			} else if (status == HttpStatus.SC_NOT_FOUND) {
				throw new RemoteInvocationFailureException(serviceName + "#" + invocation.getMethod().getName() + " is not found", null);
			} else {
				String content = EntityUtils.toString(httpResponse.getEntity());
				Map<String, String> errors = MAPPER.readValue(content, new TypeReference<Map<String, String>>() {
				});
				throw new RemoteInvocationFailureException(errors.get("type") + (errors.containsKey("message") ? " (" + errors.get("message") + ")" : ""), null);
			}
		} catch (Exception e) {
			if (e instanceof RemoteInvocationFailureException) {
				throw e;
			}

			throw new RemoteInvocationFailureException(e.getMessage(), e);
		} finally {
			if (httpPost != null) {
				httpPost.releaseConnection();
			}
		}
	}
}
