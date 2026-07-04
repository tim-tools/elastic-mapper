package io.elasticmapper.spring.scan;

import io.elasticmapper.binding.BaseMapper;
import io.elasticmapper.binding.MapperProxyFactory;
import io.elasticmapper.binding.MapperRegistry;
import io.elasticmapper.executor.ElasticTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Map;
import java.util.Set;

/**
 * Registers Mapper interface proxies as Spring beans using
 * a classpath scanner triggered by {@link MapperScan}.
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MapperScannerRegistrar.class);

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                         BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = importingClassMetadata
                .getAnnotationAttributes(MapperScan.class.getName());
        if (attrs == null) return;

        String[] basePackages = (String[]) attrs.get("value");
        if (basePackages == null || basePackages.length == 0) {
            basePackages = (String[]) attrs.get("basePackages");
        }
        if (basePackages == null || basePackages.length == 0) {
            // Default: package of the annotated class
            String className = importingClassMetadata.getClassName();
            basePackages = new String[]{className.substring(0, className.lastIndexOf('.'))};
        }

        log.info("Scanning for ElasticMapper interfaces in: {}", (Object) basePackages);

        MapperClassPathScanner scanner = new MapperClassPathScanner(registry);
        scanner.setIncludeAnnotationConfig(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseMapper.class));

        for (String basePackage : basePackages) {
            Set<BeanDefinitionHolder> beanDefinitions = scanner.doScan(basePackage);
            for (BeanDefinitionHolder holder : beanDefinitions) {
                log.info("Registered ElasticMapper: {}", holder.getBeanDefinition().getBeanClassName());
            }
        }
    }

    /**
     * Custom scanner that registers MapperFactoryBean definitions.
     */
    static class MapperClassPathScanner extends ClassPathBeanDefinitionScanner {

        MapperClassPathScanner(BeanDefinitionRegistry registry) {
            super(registry, false);
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isInterface();
        }

        @Override
        protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
            Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
            BeanDefinitionRegistry registry = getRegistry();
            for (BeanDefinitionHolder holder : holders) {
                // Replace with MapperFactoryBean — inject dependencies via constructor
                AbstractBeanDefinition factoryDef = BeanDefinitionBuilder
                        .genericBeanDefinition(MapperFactoryBean.class)
                        .addConstructorArgValue(holder.getBeanDefinition().getBeanClassName())
                        .addConstructorArgReference("elasticTemplate")
                        .addConstructorArgReference("mapperRegistry")
                        .addConstructorArgReference("xmlMapperParser")
                        .addConstructorArgReference("interceptorChain")
                        .getBeanDefinition();
                factoryDef.setPrimary(true);

                String beanName = holder.getBeanName();
                // Remove existing definition (e.g. from Spring's own component scan)
                // so MapperFactoryBean can replace it without conflicts
                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                    log.debug("Removed existing bean definition for '{}' to replace with MapperFactoryBean", beanName);
                }
                BeanDefinitionReaderUtils.registerBeanDefinition(
                        new BeanDefinitionHolder(factoryDef, beanName),
                        registry);
            }
            return holders;
        }
    }
}
