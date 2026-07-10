package io.github.timtools.elasticmapper.spring.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;

/**
 * Matches when {@code elastic-mapper.hosts} has at least one entry configured.
 * Works around the fact that {@code @ConditionalOnProperty} does not match
 * list/array properties.
 */
public class OnHostsConfiguredCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String host = context.getEnvironment().getProperty("elastic-mapper.hosts[0]");
        if (host != null && !host.trim().isEmpty()) {
            return ConditionOutcome.match("elastic-mapper.hosts is configured");
        }
        return ConditionOutcome.noMatch("elastic-mapper.hosts is not configured");
    }
}
