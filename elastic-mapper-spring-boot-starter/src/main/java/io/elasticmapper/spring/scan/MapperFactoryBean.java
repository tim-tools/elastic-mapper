package io.elasticmapper.spring.scan;

import io.elasticmapper.binding.MapperProxyFactory;
import io.elasticmapper.binding.MapperRegistry;
import io.elasticmapper.executor.ElasticTemplate;
import io.elasticmapper.parser.XMLMapperParser;
import io.elasticmapper.plugin.InterceptorChain;

import org.springframework.beans.factory.FactoryBean;

/**
 * Spring {@link FactoryBean} that produces Mapper interface proxies.
 *
 * <p>All dependencies are injected via constructor — no field-level
 * {@code @Autowired} — so missing beans cause an immediate startup
 * failure rather than a silent NPE at invocation time.
 */
public class MapperFactoryBean<T> implements FactoryBean<T> {

    private final Class<T> mapperInterface;
    private final ElasticTemplate elasticTemplate;
    private final MapperRegistry mapperRegistry;
    private final XMLMapperParser xmlMapperParser;
    private final InterceptorChain interceptorChain;

    public MapperFactoryBean(Class<T> mapperInterface,
                             ElasticTemplate elasticTemplate,
                             MapperRegistry mapperRegistry,
                             XMLMapperParser xmlMapperParser,
                             InterceptorChain interceptorChain) {
        this.mapperInterface = mapperInterface;
        this.elasticTemplate = elasticTemplate;
        this.mapperRegistry = mapperRegistry;
        this.xmlMapperParser = xmlMapperParser;
        this.interceptorChain = interceptorChain;
    }

    public MapperFactoryBean(String mapperInterfaceClassName,
                             ElasticTemplate elasticTemplate,
                             MapperRegistry mapperRegistry,
                             XMLMapperParser xmlMapperParser,
                             InterceptorChain interceptorChain) throws ClassNotFoundException {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) Class.forName(mapperInterfaceClassName);
        this.mapperInterface = clazz;
        this.elasticTemplate = elasticTemplate;
        this.mapperRegistry = mapperRegistry;
        this.xmlMapperParser = xmlMapperParser;
        this.interceptorChain = interceptorChain;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
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
