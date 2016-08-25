package ice.http.server.param;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Validation {
	private Map<String, List<Annotation>> errors = Maps.newLinkedHashMap();
	private static final ThreadLocal<Validation> current = new ThreadLocal<>();

	public static void init() {
		current.set(new Validation());
	}

	public static Validation get() {
		return current.get();
	}

	public static boolean hasErrors() {
		return !get().errors.isEmpty();
	}

	public static String getName() {
		return get().errors.isEmpty() ? null : get().errors.keySet().iterator().next();
	}

	public static void addErrors(String field, Annotation annotation) {
		List<Annotation> annotations = get().errors.get(field);

		if (annotations == null) {
			annotations = Lists.newArrayList();
		}

		annotations.add(annotation);
		get().errors.put(field, annotations);
	}

	@Override
	public String toString() {
		if (errors.isEmpty()) {
			return "no errors";
		}

		List<String> errors = Lists.newArrayList();

		for (Entry<String, List<Annotation>> entry : this.errors.entrySet()) {
			errors.add(entry.getKey() + ":" + entry.getValue());
		}

		return StringUtils.join(errors, ", ");
	}
}
