package io.github.timtools.elasticmapper.spring.scan;

import io.github.timtools.elasticmapper.binding.MapperProxyFactory;
import io.github.timtools.elasticmapper.binding.MapperRegistry;
import io.github.timtools.elasticmapper.executor.ElasticTemplate;
import io.github.timtools.elasticmapper.parser.XMLMapperParser;
import io.github.timtools.elasticmapper.plugin.InterceptorChain;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring {@link FactoryBean} that produces Mapper interface proxies.
 *
 * <p>Dependencies ({@link ElasticTemplate}, {@link MapperRegistry},
 * {@link XMLMapperParser}, {@link InterceptorChain}) are resolved
 * <b>lazily</b> from the {@link ApplicationContext} in {@link #getObject()}
 * rather than via constructor injection.
 *
 * <p>This avoids forward-reference problems: the mapper bean definition
 * is registered by {@link MapperScannerRegistrar} (an
 * {@code ImportBeanDefinitionRegistrar} that runs immediately) before
 * the auto-configuration that defines {@code elasticTemplate} etc.
 * (processed as a {@code DeferredImportSelector} that runs later).
 * By deferring resolution to {@code getObject()}, all beans are
 * guaranteed to exist by the time the proxy is created.
 */
public class MapperFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private final Class<T> mapperInterface;
    private ApplicationContext applicationContext;

    public MapperFactoryBean(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    /**
     * Constructor used by {@link MapperScannerRegistrar} (class-name form,
     * since the mapper interface may not be loadable at registration time).
     */
    public MapperFactoryBean(String mapperInterfaceClassName) throws ClassNotFoundException {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) Class.forName(mapperInterfaceClassName);
        this.mapperInterface = clazz;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        // Resolve dependencies lazily — by the time getObject() is called
        // the auto-configuration has run and all beans are available.
        ElasticTemplate elasticTemplate = applicationContext.getBean(ElasticTemplate.class);
        MapperRegistry mapperRegistry = applicationContext.getBean(MapperRegistry.class);
        XMLMapperParser xmlMapperParser = applicationContext.getBean(XMLMapperParser.class);
        InterceptorChain interceptorChain = applicationContext.getBean(InterceptorChain.class);
        return (T) MapperProxyFactory.createMapper(mapperInterface, elasticTemplate,
                mapperRegistry, xmlMapperParser, interceptorChain);
    }

    @Override
    public Class<?> getObjectType() {
        return mapperInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
