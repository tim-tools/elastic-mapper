package io.github.timtools.elasticmapper.binding;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that holds all Mapper interface proxies and their method mappings.
 * Each Mapper interface gets a single proxy instance, and each method on it
 * gets a {@link MapperMethod} descriptor.
 */
public class MapperRegistry {

    /** Mapper interface class → proxy instance */
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();

    /** "interface.method" → MapperMethod descriptor */
    private final Map<String, MapperMethod> methodCache = new ConcurrentHashMap<>();

    /**
     * Registers a Mapper interface and its proxy.
     */
    public void register(Class<?> mapperInterface, Object proxy) {
        proxyCache.put(mapperInterface, proxy);
    }

    /**
     * Returns the proxy instance for a Mapper interface.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMapper(Class<T> mapperInterface) {
        return (T) proxyCache.get(mapperInterface);
    }

    /**
     * Returns true if the Mapper interface is registered.
     */
    public boolean hasMapper(Class<?> mapperInterface) {
        return proxyCache.containsKey(mapperInterface);
    }

    /**
     * Caches a method descriptor.
     */
    public void putMethod(Class<?> mapperInterface, Method method, MapperMethod mapperMethod) {
        methodCache.put(methodKey(mapperInterface, method), mapperMethod);
    }

    /**
     * Retrieves a cached method descriptor.
     */
    public MapperMethod getMethod(Class<?> mapperInterface, Method method) {
        return methodCache.get(methodKey(mapperInterface, method));
    }

    /**
     * Returns all registered method descriptors.
     */
    public Map<String, MapperMethod> getAllMethods() {
        return new ConcurrentHashMap<>(methodCache);
    }

    /**
     * Clears all registrations.
     */
    public void clear() {
        proxyCache.clear();
        methodCache.clear();
    }

    private static String methodKey(Class<?> mapperInterface, Method method) {
        return mapperInterface.getName() + "." + method.getName();
    }
}
