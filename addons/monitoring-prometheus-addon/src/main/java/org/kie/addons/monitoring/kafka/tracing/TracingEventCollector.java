package org.kie.addons.monitoring.kafka.tracing;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;

import io.cloudevents.v1.CloudEventBuilder;
import io.cloudevents.v1.CloudEventImpl;
import io.reactivex.BackpressureStrategy;
import io.reactivex.subjects.PublishSubject;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.kie.addons.monitoring.kafka.tracing.types.DMNResultDTO;
import org.kie.kogito.dmn.rest.DMNResult;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TracingEventCollector {

    private static final Logger LOG = LoggerFactory.getLogger(TracingEventCollector.class);

    private final PublishSubject<String> eventSubject = PublishSubject.create();

    public String handleEvent(DMNResult result) {
        try {
            LOG.trace("TracingEventCollector::handleEvent");

            Map<String, Object> dataMap = new HashMap<>();

            String evaluationId = UUID.randomUUID().toString();
            DMNResultDTO resultDto = new DMNResultDTO(evaluationId, result);

            dataMap.put("result", resultDto);

            CloudEventImpl<Map<String, Object>> cloudEvent =
                    CloudEventBuilder.<Map<String, Object>>builder()
                            .withType("String")
                            .withId(UUID.randomUUID().toString())
                            .withSource(URI.create(String.format("%s/%s",
                                                                 result.getNamespace(),
                                                                 result.getModelName())
                            ))
                            .withData(dataMap)
                            .build();

            String payload = io.cloudevents.json.Json.encode(cloudEvent);

            LOG.trace("Payload: {}", payload);

            eventSubject.onNext(payload);
            return cloudEvent.getAttributes().getId();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    @Outgoing("kogito-tracing")
    public Publisher<String> getEventPublisher() {
        return eventSubject.toFlowable(BackpressureStrategy.BUFFER);
    }

    private static String urlEncode(String input) {
        try {
            return URLEncoder.encode(input, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

}
