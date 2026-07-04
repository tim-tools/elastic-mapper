package io.elasticmapper.plugin;

import io.elasticmapper.binding.MethodInvocation;

/**
 * Interceptor interface for ElasticMapper query pipeline.
 * Similar to MyBatis Plugin/Interceptor pattern.
 */
public interface Interceptor {

    /**
     * Called before the query is executed.
     *
     * @param invocation the method invocation (method, args, param map)
     */
    void before(MethodInvocation invocation);

    /**
     * Called after the query completes (even if it throws).
     *
     * @param invocation the method invocation
     * @param result     the query result, or null if the query threw
     */
    void after(MethodInvocation invocation, Object result);
}
