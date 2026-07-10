package io.github.timtools.elasticmapper.spring.scan;

import io.github.timtools.elasticmapper.binding.BaseMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Map;
import java.util.Set;

/**
 * Registers Mapper interface proxies as Spring beans using
 * a classpath scanner triggered by {@link ESMapperScan}.
 *
 * <p>Uses {@link ClassPathScanningCandidateComponentProvider} directly
 * rather than {@code ClassPathBeanDefinitionScanner} to avoid the
 * temporary-bean-definition + replace pattern that can trigger
 * premature / duplicate auto-configuration processing.
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MapperScannerRegistrar.class);

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                         BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = importingClassMetadata
                .getAnnotationAttributes(ESMapperScan.class.getName());
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

        // Use ClassPathScanningCandidateComponentProvider directly —
        // NOT ClassPathBeanDefinitionScanner. The scanner's doScan()
        // internally registers a temporary bean definition for each
        // interface it finds, then we replace it with MapperFactoryBean.
        // That double-registration can trigger unwanted side effects
        // during ConfigurationClassPostProcessor processing (re-evaluation
        // of auto-configuration classes, duplicate @EnableConfigurationProperties
        // handling, etc.).
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // Accept only interfaces — mapper interfaces must be interfaces
                return beanDefinition.getMetadata().isInterface();
            }
        };
        provider.addIncludeFilter(new AssignableTypeFilter(BaseMapper.class));

        if (registry instanceof ResourceLoader) {
            provider.setResourceLoader((ResourceLoader) registry);
        }

        AnnotationBeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = provider.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                String beanClassName = candidate.getBeanClassName();
                String beanName = beanNameGenerator.generateBeanName(candidate, registry);

                // Register MapperFactoryBean directly — dependencies
                // (elasticTemplate, mapperRegistry, etc.) are resolved
                // lazily via ApplicationContext in getObject(), avoiding
                // forward-reference issues with auto-configuration beans.
                AbstractBeanDefinition factoryDef = BeanDefinitionBuilder
                        .genericBeanDefinition(MapperFactoryBean.class)
                        .addConstructorArgValue(beanClassName)
                        .getBeanDefinition();
                factoryDef.setPrimary(true);

                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                    log.debug("Removed existing bean definition for '{}' to replace with MapperFactoryBean", beanName);
                }
                registry.registerBeanDefinition(beanName, factoryDef);
                log.info("Registered ElasticMapper: {}", beanClassName);
            }
        }
    }
}
