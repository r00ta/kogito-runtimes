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
package org.kie.kogito.services.event.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessEvent;
import org.kie.api.event.process.ProcessNodeEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.kogito.Addons;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.event.EventBatch;
import org.kie.kogito.internal.process.event.ProcessWorkItemTransitionEvent;
import org.kie.kogito.internal.process.event.KogitoProcessVariableChangedEvent;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkItem;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.kie.kogito.process.workitem.HumanTaskWorkItem;
import org.kie.kogito.services.event.ProcessInstanceDataEvent;
import org.kie.kogito.services.event.UserTaskInstanceDataEvent;
import org.kie.kogito.services.event.VariableInstanceDataEvent;

public class ProcessInstanceEventBatch implements EventBatch {

    public static final String TRACKED = "tracked";

    private final String service;
    private Addons addons;
    private List<ProcessEvent> rawEvents = new ArrayList<>();

    public ProcessInstanceEventBatch(String service, Addons addons) {
        this.service = service;
        this.addons = addons;
    }

    @Override
    public void append(Object rawEvent) {
        if (rawEvent instanceof ProcessEvent) {
            rawEvents.add((ProcessEvent) rawEvent);
        }
    }

    @Override
    public Collection<DataEvent<?>> events() {
        Map<String, ProcessInstanceEventBody> processInstances = new LinkedHashMap<>();
        Map<String, UserTaskInstanceEventBody> userTaskInstances = new LinkedHashMap<>();
        Set<VariableInstanceEventBody> variables = new LinkedHashSet<>();

        for (ProcessEvent event : rawEvents) {
            ProcessInstanceEventBody body = processInstances.computeIfAbsent( ((KogitoProcessInstance) event.getProcessInstance()).getStringId(), key -> create(event));

            if (event instanceof ProcessNodeTriggeredEvent) {
                handleProcessNodeTriggeredEvent((ProcessNodeTriggeredEvent) event, body);
            } else if (event instanceof ProcessNodeLeftEvent) {
                handleProcessNodeLeftEvent((ProcessNodeLeftEvent) event, body);
            } else if (event instanceof ProcessCompletedEvent) {
                handleProcessCompletedEvent((ProcessCompletedEvent) event, body);
            } else if (event instanceof ProcessWorkItemTransitionEvent) {
                handleProcessWorkItemTransitionEvent((ProcessWorkItemTransitionEvent) event, userTaskInstances);
            } else if (event instanceof ProcessVariableChangedEvent) {
                handleProcessVariableChangedEvent((KogitoProcessVariableChangedEvent) event, variables);
            }
        }

        Collection<DataEvent<?>> processedEvents = new ArrayList<>();

        processInstances.values().stream().map(pi -> new ProcessInstanceDataEvent(extractRuntimeSource(pi.metaData()), addons.toString(), pi.metaData(), pi)).forEach(processedEvents::add);
        userTaskInstances.values().stream().map(pi -> new UserTaskInstanceDataEvent(extractRuntimeSource(pi.metaData()), addons.toString(), pi.metaData(), pi)).forEach(processedEvents::add);
        variables.stream().map(pi -> new VariableInstanceDataEvent(extractRuntimeSource(pi.metaData()), addons.toString(), pi.metaData(), pi)).forEach(processedEvents::add);

        return processedEvents;
    }

    protected void handleProcessCompletedEvent(ProcessCompletedEvent event, ProcessInstanceEventBody body) {
        // in case this is a process complete event always updated and date and state 
        body.update()
                .endDate((( KogitoWorkflowProcessInstance ) event.getProcessInstance()).getEndDate())
                .state(event.getProcessInstance().getState());
    }

    protected void handleProcessNodeTriggeredEvent(ProcessNodeTriggeredEvent event, ProcessInstanceEventBody body) {
        NodeInstanceEventBody nodeInstanceBody = create((ProcessNodeEvent) event);
        if (!body.getNodeInstances().contains(nodeInstanceBody)) {
            // add it only if it does not exist
            body.update().nodeInstance(nodeInstanceBody);
        }
    }

    protected void handleProcessNodeLeftEvent(ProcessNodeLeftEvent event, ProcessInstanceEventBody body) {
        NodeInstanceEventBody nodeInstanceBody = create((ProcessNodeEvent) event);
        // if it's already there, remove it
        body.getNodeInstances().remove(nodeInstanceBody);
        // and add it back as the node left event has latest information
        body.update().nodeInstance(nodeInstanceBody);
    }

    protected void handleProcessWorkItemTransitionEvent(ProcessWorkItemTransitionEvent workItemTransitionEvent, Map<String, UserTaskInstanceEventBody> userTaskInstances) {
        KogitoWorkItem workItem = workItemTransitionEvent.getWorkItem();
        if (workItem instanceof HumanTaskWorkItem && workItemTransitionEvent.isTransitioned()) {
            userTaskInstances.putIfAbsent(workItem.getStringId(), createUserTask(workItemTransitionEvent));
        }
    }

    protected void handleProcessVariableChangedEvent(KogitoProcessVariableChangedEvent variableChangedEvent, Set<VariableInstanceEventBody> variables) {
        if (variableChangedEvent.hasTag(TRACKED)) {
            variables.add(create(variableChangedEvent));
        }
    }

    protected UserTaskInstanceEventBody createUserTask(ProcessWorkItemTransitionEvent workItemTransitionEvent) {
        KogitoWorkflowProcessInstance pi = (KogitoWorkflowProcessInstance) workItemTransitionEvent.getProcessInstance();
        HumanTaskWorkItem workItem = (HumanTaskWorkItem) workItemTransitionEvent.getWorkItem();
        return UserTaskInstanceEventBody.create()
                .id(workItem.getStringId())
                .state(workItem.getPhaseStatus())
                .taskName(workItem.getTaskName())
                .taskDescription(workItem.getTaskDescription())
                .taskPriority(workItem.getTaskPriority())
                .referenceName(workItem.getReferenceName())
                .actualOwner(workItem.getActualOwner())
                .startDate(workItem.getStartDate())
                .completeDate(workItem.getCompleteDate())
                .adminGroups(workItem.getAdminGroups())
                .adminUsers(workItem.getAdminUsers())
                .excludedUsers(workItem.getExcludedUsers())
                .potentialGroups(workItem.getPotentialGroups())
                .potentialUsers(workItem.getPotentialUsers())
                .processInstanceId(pi.getStringId())
                .rootProcessInstanceId(pi.getRootProcessInstanceId())
                .processId(pi.getProcessId())
                .rootProcessId(pi.getRootProcessId())
                .inputs(workItem.getParameters())
                .outputs(workItem.getResults())
                .build();
    }

    protected ProcessInstanceEventBody create(ProcessEvent event) {
        KogitoWorkflowProcessInstance pi = (KogitoWorkflowProcessInstance) event.getProcessInstance();

        ProcessInstanceEventBody.Builder eventBuilder = ProcessInstanceEventBody.create()
                .id(pi.getStringId())
                .parentInstanceId(pi.getParentProcessInstanceStringId())
                .rootInstanceId(pi.getRootProcessInstanceId())
                .processId(pi.getProcessId())
                .rootProcessId(pi.getRootProcessId())
                .processName(pi.getProcessName())
                .startDate(pi.getStartDate())
                .endDate(pi.getEndDate())
                .state(pi.getState())
                .businessKey(pi.getCorrelationKey())
                .variables(pi.getVariables())
                .milestones(createMilestones(pi));

        if (pi.getState() == KogitoProcessInstance.STATE_ERROR) {
            eventBuilder.error(ProcessErrorEventBody.create()
                                       .nodeDefinitionId(pi.getNodeIdInError())
                                       .errorMessage(pi.getErrorMessage())
                                       .build());
        }

        String securityRoles = (String) pi.getProcess().getMetaData().get("securityRoles");
        if (securityRoles != null) {
            eventBuilder.roles(securityRoles.split(","));
        }

        return eventBuilder.build();
    }

    protected Set<MilestoneEventBody> createMilestones(KogitoWorkflowProcessInstance pi) {
        if (pi.milestones() == null) {
            return null;
        }

        return pi.milestones().stream()
                .map(m -> MilestoneEventBody.create().id(m.getId()).name(m.getName()).status(m.getStatus().name()).build())
                .collect(Collectors.toSet());
    }

    protected NodeInstanceEventBody create(ProcessNodeEvent event) {
        KogitoNodeInstance ni = ( KogitoNodeInstance ) event.getNodeInstance();

        return NodeInstanceEventBody.create()
                .id(ni.getStringId())
                .nodeId(String.valueOf(ni.getNodeId()))
                .nodeDefinitionId(ni.getNodeDefinitionId())
                .nodeName(ni.getNodeName())
                .nodeType(ni.getNode().getClass().getSimpleName())
                .triggerTime(ni.getTriggerTime())
                .leaveTime(ni.getLeaveTime())
                .build();
    }

    protected VariableInstanceEventBody create(KogitoProcessVariableChangedEvent event) {
        KogitoProcessInstance pi = (KogitoProcessInstance) event.getProcessInstance();

        VariableInstanceEventBody.Builder eventBuilder = VariableInstanceEventBody.create()
                .changeDate(event.getEventDate())
                .processId(pi.getProcessId())
                .processInstanceId(pi.getStringId())
                .rootProcessId(pi.getRootProcessId())
                .rootProcessInstanceId(pi.getRootProcessInstanceId())
                .variableName(event.getVariableId())
                .variableValue(event.getNewValue())
                .variablePreviousValue(event.getOldValue());

        if (event.getNodeInstance() != null) {
            eventBuilder
                    .changedByNodeId(event.getNodeInstance().getNodeDefinitionId())
                    .changedByNodeName(event.getNodeInstance().getNodeName())
                    .changedByNodeType(event.getNodeInstance().getNode().getClass().getSimpleName());
        }

        return eventBuilder.build();
    }

    protected String extractRuntimeSource(Map<String, String> metadata) {
        String processId = metadata.get(ProcessInstanceEventBody.PROCESS_ID_META_DATA);
        if(processId == null){
            return null;
        } else {
            return service + "/" + (processId.contains(".") ? processId.substring(processId.lastIndexOf('.') + 1) : processId);
        }
    }
}
