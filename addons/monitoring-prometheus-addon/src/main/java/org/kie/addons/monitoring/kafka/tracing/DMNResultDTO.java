package org.kie.addons.monitoring.kafka.tracing;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNResult;

public class DMNResultDTO {
    @JsonProperty("decisions")
    public Map<String, String> decisions;

    @JsonProperty("modelName")
    public String modelName;

    @JsonProperty("modelNamespace")
    public String modelNamespace;

    public DMNResultDTO(String modelName, String modelNamespace, Map<String, String> decisions){
        this.decisions = decisions;
        this.modelName = modelName;
        this.modelNamespace = modelNamespace;
    }

    public DMNResultDTO(DMNResult result){
        //
    }
}
