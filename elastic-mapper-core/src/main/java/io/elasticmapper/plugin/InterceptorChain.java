package io.elasticmapper.plugin;

import io.elasticmapper.binding.MethodInvocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a chain of interceptors, executing them in order before/after queries.
 * Thread-safe: interceptors can be added/removed at runtime.
 */
public class InterceptorChain {

    private static final Logger log = LoggerFactory.getLogger(InterceptorChain.class);

    private final List<Interceptor> interceptors = new CopyOnWriteArrayList<>();

    public void addInterceptor(Interceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
        }
    }

    public void addInterceptors(List<Interceptor> list) {
        if (list != null) {
            interceptors.addAll(list);
        }
    }

    public void removeInterceptor(Interceptor interceptor) {
        interceptors.remove(interceptor);
    }

    public void clear() {
        interceptors.clear();
    }

    public List<Interceptor> getInterceptors() {
        return Collections.unmodifiableList(new ArrayList<>(interceptors));
    }

    /**
     * Executes all before() callbacks. A single interceptor failure
     * is logged but does not stop the chain.
     */
    public void fireBefore(MethodInvocation invocation) {
        for (Interceptor interceptor : interceptors) {
            try {
                interceptor.before(invocation);
            } catch (Exception e) {
                log.error("Interceptor.before() error: {}", interceptor.getClass().getName(), e);
            }
        }
    }

    /**
     * Executes all after() callbacks in reverse order.
     */
    public void fireAfter(MethodInvocation invocation, Object result) {
        // Reverse order for after() callbacks
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            try {
                interceptors.get(i).after(invocation, result);
            } catch (Exception e) {
                log.error("Interceptor.after() error: {}", interceptors.get(i).getClass().getName(), e);
            }
        }
    }
}
