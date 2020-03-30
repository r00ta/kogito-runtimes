package org.kie.addons.monitoring.kafka.tracing.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;

public class DecisionResultDto {
    @JsonProperty("decisionId")
    public String decisionId;

    @JsonProperty("decisionName")
    public String decisionName;

    @JsonProperty("evaluationStatus")
    public DecisionEvaluationStatusDto evaluationStatus;

    @JsonProperty("decisionResult")
    public Object result;

    // TODO: create dto properly
    @JsonProperty("messages")
    public List<DMNMessage> messages;

    @JsonProperty("hasErrors")
    public boolean hasErrors;

    public DecisionResultDto(DMNDecisionResult result){
        this.decisionName = result.getDecisionName();
        this.decisionId = result.getDecisionId();
        this.evaluationStatus = DecisionEvaluationStatusDto.convert(result.getEvaluationStatus());
        this.result = result.getResult();
        this.messages = result.getMessages();
        this.hasErrors = result.hasErrors();
    }
}
