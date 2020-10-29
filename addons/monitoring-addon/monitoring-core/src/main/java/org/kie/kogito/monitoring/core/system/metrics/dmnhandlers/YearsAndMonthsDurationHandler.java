/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.monitoring.core.system.metrics.dmnhandlers;

import java.time.Period;

import io.micrometer.core.instrument.MeterRegistry;
import org.kie.kogito.monitoring.core.MonitoringRegistry;

public class YearsAndMonthsDurationHandler extends TypeHandlerWithSummary<Period> {

    private final String dmnType;

    public YearsAndMonthsDurationHandler(String dmnType) {
        this(dmnType, MonitoringRegistry.getDefaultMeterRegistry());
    }

    public YearsAndMonthsDurationHandler(String dmnType, MeterRegistry meterRegistry){
        this.dmnType = dmnType;
        this.registry = meterRegistry;
    }

    @Override
    public void record(String type, String endpointName, Period sample) {
        getDefaultSummary(dmnType, type, endpointName).record(sample.toTotalMonths());
    }

    @Override
    public String getDmnType() {
        return dmnType;
    }
}
