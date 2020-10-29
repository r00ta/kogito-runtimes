/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.monitoring.core.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.SLAViolatedEvent;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.kogito.monitoring.core.MonitoringRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusProcessEventListener extends DefaultProcessEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusProcessEventListener.class);

    private static Counter getNumberOfProcessInstancesStartedCounter(String appId, String processId) {
        List<Tag> tags = new ArrayList<Tag>() {
            {
                add(Tag.of("app_id", appId));
                add(Tag.of("process_id", processId));
            }
        };
        return Counter
                .builder("kie_process_instance_started_total")
                .description("Started Process Instances")
                .tags(tags)
                .register(MonitoringRegistry.getDefaultMeterRegistry());
    }

    private static Counter getNumberOfSLAsViolatedCounter(String appId, String processId, String nodeName) {
        List<Tag> tags = new ArrayList<Tag>() {
            {
                Tag.of("app_id", appId);
                Tag.of("process_id", processId);
                Tag.of("node_name", nodeName);
            }
        };
        return Counter
                .builder("kie_process_instance_sla_violated_total")
                .description("Process Instances SLA Violated")
                .tags(tags)
                .register(MonitoringRegistry.getDefaultMeterRegistry());
    }

    private static Map<String, AtomicInteger> gaugeMap = new HashMap<String, AtomicInteger>();

    private static Counter getNumberOfProcessInstancesCompletedCounter(String appId, String processId, String nodeName) {
        List<Tag> tags = new ArrayList<Tag>() {
            {
                Tag.of("app_id", appId);
                Tag.of("process_id", processId);
                Tag.of("node_name", nodeName);
            }
        };
        return Counter
                .builder("kie_process_instance_completed_total")
                .description("Completed Process Instances")
                .tags(tags)
                .register(MonitoringRegistry.getDefaultMeterRegistry());
    }

    private static AtomicInteger getRunningProcessInstancesGauge(String appId, String processId) {
        if (gaugeMap.containsKey(appId + processId)) {
            return gaugeMap.get(appId + processId);
        }
        List<Tag> tags = new ArrayList<Tag>() {
            {
                add(Tag.of("app_id", appId));
                add(Tag.of("process_id", processId));
            }
        };
        AtomicInteger atomicInteger = new AtomicInteger(0);
        Gauge.builder("kie_process_instance_running_total", atomicInteger, AtomicInteger::doubleValue)
                .description("Running Process Instances")
                .tags(tags)
                .register(MonitoringRegistry.getDefaultMeterRegistry());
        gaugeMap.put(appId + processId, atomicInteger);
        return atomicInteger;
    }

    private static DistributionSummary getProcessInstancesDurationSummary(String appId, String processId) {
        List<Tag> tags = new ArrayList<Tag>() {
            {
                add(Tag.of("app_id", appId));
                add(Tag.of("process_id", processId));
            }
        };
        return DistributionSummary.builder("kie_process_instance_duration_seconds")
                .description("Process Instances Duration")
                .tags(tags)
                .register(MonitoringRegistry.getDefaultMeterRegistry());
    }

    private static DistributionSummary getWorkItemsDurationSummary(String name) {
        List<Tag> tags = new ArrayList<Tag>() {
            {
                add(Tag.of("name", name));
            }
        };
        return DistributionSummary.builder("kie_work_item_duration_seconds")
                .description("Work Items Duration")
                .tags(tags)
                .register(MonitoringRegistry.getDefaultMeterRegistry());
    }

    protected static void recordRunningProcessInstance(String containerId, String processId) {
        getRunningProcessInstancesGauge(containerId, processId).incrementAndGet();
    }

    private String identifier;

    public PrometheusProcessEventListener(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public void afterProcessStarted(ProcessStartedEvent event) {
        LOGGER.debug("After process started event: {}", event);
        final WorkflowProcessInstanceImpl processInstance = (WorkflowProcessInstanceImpl) event.getProcessInstance();
        getNumberOfProcessInstancesStartedCounter(identifier, processInstance.getProcessId()).increment();
        recordRunningProcessInstance(identifier, processInstance.getProcessId());
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        LOGGER.debug("After process completed event: {}", event);
        final WorkflowProcessInstanceImpl processInstance = (WorkflowProcessInstanceImpl) event.getProcessInstance();
        getRunningProcessInstancesGauge(identifier, processInstance.getProcessId()).decrementAndGet();

        getNumberOfProcessInstancesCompletedCounter(identifier, processInstance.getProcessId(), String.valueOf(processInstance.getState())).increment();

        if (processInstance.getStartDate() != null) {
            final double duration = millisToSeconds(processInstance.getEndDate().getTime() - processInstance.getStartDate().getTime());
            getProcessInstancesDurationSummary(identifier, processInstance.getProcessId()).record(duration);
            LOGGER.debug("Process Instance duration: {}s", duration);
        }
    }

    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent event) {
        LOGGER.debug("Before Node left event: {}", event);
        final NodeInstance nodeInstance = event.getNodeInstance();
        if (nodeInstance instanceof WorkItemNodeInstance) {
            WorkItemNodeInstance wi = (WorkItemNodeInstance) nodeInstance;
            if (wi.getTriggerTime() != null) {
                final String name = (String) wi.getWorkItem().getParameters().getOrDefault("TaskName", wi.getWorkItem().getName());
                final double duration = millisToSeconds(wi.getLeaveTime().getTime() - wi.getTriggerTime().getTime());
                getWorkItemsDurationSummary(name).record(duration);
                LOGGER.debug("Work Item {}, duration: {}s", name, duration);
            }
        }
    }

    @Override
    public void afterSLAViolated(SLAViolatedEvent event) {
        LOGGER.debug("After SLA violated event: {}", event);
        final WorkflowProcessInstanceImpl processInstance = (WorkflowProcessInstanceImpl) event.getProcessInstance();
        if (processInstance != null && event.getNodeInstance() != null) {
            getNumberOfSLAsViolatedCounter(identifier, processInstance.getProcessId(), event.getNodeInstance().getNodeName()).increment();
        }
    }

    protected static double millisToSeconds(long millis) {
        return millis / 1000.0;
    }
}
