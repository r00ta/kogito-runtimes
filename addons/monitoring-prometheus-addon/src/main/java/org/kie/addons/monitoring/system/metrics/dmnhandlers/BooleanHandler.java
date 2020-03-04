package org.kie.addons.monitoring.system.metrics.dmnhandlers;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.SimpleCollector;
import org.kie.addons.monitoring.system.metrics.MetricsConstants;

public class BooleanHandler implements TypeHandler<Boolean>{

    private final Counter counter;

    public BooleanHandler(String prefix, CollectorRegistry registry){
        this.counter = initializeCounter(prefix, registry);
    }

    public BooleanHandler(String prefix){
        this.counter = initializeCounter(prefix, null);
    }

    private Counter initializeCounter(String prefix, CollectorRegistry registry){
        Counter.Builder builder = Counter.build().name(prefix + MetricsConstants.DECISIONS_NAME_SUFFIX)
                                                .help(MetricsConstants.DECISIONS_HELP)
                                                .labelNames(MetricsConstants.HANDLER_IDENTIFIER_LABELS);

        Counter counter = registry == null ? builder.register(CollectorRegistry.defaultRegistry) : builder.register(registry);

        return counter;
    }

    @Override
    public void record(String handler, Boolean sample) {
        counter.labels(handler, sample.toString()).inc();
    }
}
