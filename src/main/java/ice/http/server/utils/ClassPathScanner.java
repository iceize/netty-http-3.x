package ice.http.server.utils;

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.Set;

public final class ClassPathScanner {
	private ClassPathScanner() {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	public static <T> Set<Class<? extends T>> scan(String basePackage, Filter filter) {
		Set<Class<? extends T>> classes = Sets.newHashSet();
		ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
		MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

		Resource[] resources = null;

		try {
			resources = resourcePatternResolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + StringUtils.replace(basePackage, ".", "/") + "/**/*.class");
		} catch (IOException ignored) {
		}

		if (resources == null) {
			return classes;
		}

		for (Resource resource : resources) {
			if (resource.isReadable()) {
				try {
					MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);

					if (filter.matches(metadataReader)) {
						classes.add((Class<T>) ClassUtils.forName(metadataReader.getClassMetadata().getClassName(), ClassUtils.getDefaultClassLoader()));
					}
				} catch (Exception ignored) {
				}
			}
		}

		return classes;
	}

	interface Filter {
		boolean matches(MetadataReader metadataReader);
	}

	public static class AnnotationFilter implements Filter {
		private final Class<?> type;

		public AnnotationFilter(Class<?> type) {
			this.type = type;
		}

		@Override
		public boolean matches(MetadataReader metadataReader) {
			return metadataReader.getAnnotationMetadata().getAnnotationTypes().contains(type.getName());
		}
	}

	public static class AssignableFilter implements Filter {
		private final Class<?> type;

		public AssignableFilter(Class<?> type) {
			this.type = type;
		}

		@Override
		public boolean matches(MetadataReader metadataReader) {
			try {
				Class<?> clazz = ClassUtils.forName(metadataReader.getClassMetadata().getClassName(), ClassUtils.getDefaultClassLoader());
				return !clazz.isInterface() && type.isAssignableFrom(clazz);
			} catch (Exception ignored) {
			}

			return false;
		}
	}
}
