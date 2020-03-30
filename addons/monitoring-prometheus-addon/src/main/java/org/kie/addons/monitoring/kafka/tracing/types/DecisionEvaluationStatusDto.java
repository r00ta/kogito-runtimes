package org.kie.addons.monitoring.kafka.tracing.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.kie.dmn.api.core.DMNDecisionResult;

public enum DecisionEvaluationStatusDto {
    @JsonProperty("NOT_EVALUATED")
    NOT_EVALUATED,
    @JsonProperty("EVALUATING")
    EVALUATING,
    @JsonProperty("SUCCEEDED")
    SUCCEEDED,
    @JsonProperty("SKIPPED")
    SKIPPED,
    @JsonProperty("FAILED")
    FAILED;

    public static DecisionEvaluationStatusDto convert(DMNDecisionResult.DecisionEvaluationStatus status){
        switch(status){
            case SUCCEEDED:
                return SUCCEEDED;
            case NOT_EVALUATED:
                return NOT_EVALUATED;
            case EVALUATING:
                return EVALUATING;
            case SKIPPED:
                return SKIPPED;
            case FAILED:
                return FAILED;
            default:
                throw new IllegalArgumentException("Unsupported evaluation status.");
        }
    }
}
