package org.kie.addons.monitoring.kafka.tracing.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.kogito.dmn.rest.DMNResult;

public class DMNResultDTO {

    @JsonProperty("decisions")
    public List<DecisionResultDto> decisions;

    @JsonProperty("evaluationId")
    public String evaluationId;

    @JsonProperty("evaluationTimestamp")
    public Long evaluationTimestamp;

    @JsonProperty("context")
    public Map<String, Object> context;

    @JsonProperty("modelNamespace")
    public String modelNamespace;

    @JsonProperty("modelName")
    public String modelName;

    public DMNResultDTO(String evaluationId, DMNResult result){
        this.evaluationId = evaluationId;
        this.decisions = result.getDecisionResults().stream().map(x -> new DecisionResultDto(x)).collect(Collectors.toList());
        this.evaluationDate = System.currentTimeMillis();
        this.context = result.getContext().getAll();
        this.modelNamespace = result.getNamespace();
        this.modelName = result.getModelName();
    }
}
