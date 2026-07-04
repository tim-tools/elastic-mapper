package io.elasticmapper.binding;

import io.elasticmapper.executor.ElasticTemplate;
import io.elasticmapper.metadata.EntityMetadata;
import io.elasticmapper.metadata.MetadataParser;
import io.elasticmapper.parser.XMLMapperParser;
import io.elasticmapper.plugin.InterceptorChain;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

/**
 * Creates JDK dynamic proxy instances for Mapper interfaces.
 * Each proxy routes method calls through {@link MapperProxy}.
 */
public final class MapperProxyFactory {

    private MapperProxyFactory() {
        // factory class
    }

    /**
     * Creates a proxy instance for the given Mapper interface.
     *
     * @param mapperInterface the Mapper interface (must extend {@link BaseMapper})
     * @param template        the ElasticTemplate for ES communication
     * @param registry        the MapperRegistry for method lookups
     * @param <T>             the entity type
     * @return a proxy implementing the Mapper interface
     */
    @SuppressWarnings("unchecked")
    public static <T> T createMapper(Class<?> mapperInterface,
                                      ElasticTemplate template,
                                      MapperRegistry registry,
                                      XMLMapperParser xmlParser,
                                      InterceptorChain interceptorChain) {
        Class<?> entityClass = resolveEntityClass(mapperInterface);

        MapperProxy handler = new MapperProxy(template, registry, mapperInterface,
                entityClass, xmlParser, interceptorChain);
        T proxy = (T) Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class<?>[]{mapperInterface},
                handler);

        // Register in cache
        registry.register(mapperInterface, proxy);

        return proxy;
    }

    /**
     * Resolves the entity type from a Mapper interface that extends BaseMapper<T>.
     */
    public static Class<?> resolveEntityClass(Class<?> mapperInterface) {
        // Walk up the interface hierarchy to find BaseMapper
        for (Type genericInterface : mapperInterface.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                Type rawType = pt.getRawType();
                if (rawType instanceof Class && BaseMapper.class.isAssignableFrom((Class<?>) rawType)) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        return (Class<?>) typeArgs[0];
                    }
                }
            }
            // Recurse into parent interfaces
            if (genericInterface instanceof Class) {
                Class<?> resolved = resolveEntityClass((Class<?>) genericInterface);
                if (resolved != null && resolved != Object.class) {
                    return resolved;
                }
            }
        }
        throw new IllegalArgumentException(
                "Cannot resolve entity type from Mapper interface: " + mapperInterface.getName() +
                ". Ensure the interface extends BaseMapper<T> with a concrete type parameter.");
    }
}
