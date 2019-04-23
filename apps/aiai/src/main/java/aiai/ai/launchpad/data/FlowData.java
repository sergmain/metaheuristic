/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.data;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.api.v1.EnumsApi;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class TaskProducingResult extends BaseDataClass {
        public EnumsApi.FlowProducingStatus flowProducingStatus;
        public EnumsApi.FlowValidateStatus flowValidateStatus;
        public int numberOfTasks = 0;

        public TaskProducingResult(List<String> errorMessages, EnumsApi.FlowValidateStatus flowValidateStatus,
                                   EnumsApi.FlowProducingStatus flowProducingStatus) {
            this.errorMessages = errorMessages;
            this.flowValidateStatus = flowValidateStatus;
            this.flowProducingStatus = flowProducingStatus;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class FlowsResult extends BaseDataClass {
        public Slice<Flow> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowResult extends BaseDataClass {
        public Flow flow;
        public EnumsApi.FlowValidateStatus status = EnumsApi.FlowValidateStatus.NOT_VERIFIED_YET;

        public FlowResult(String errorMessage, EnumsApi.FlowValidateStatus status) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public FlowResult(List<String> errorMessage, EnumsApi.FlowValidateStatus status) {
            this.status = status;
            this.errorMessages = errorMessage;
        }

        public FlowResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public FlowResult(Flow flow) {
            this.flow = flow;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class FlowInstancesResult extends BaseDataClass {
        public Slice<FlowInstance> instances;
        public long currentFlowId;
        public Map<Long, Flow> flows = new HashMap<>();

    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowInstanceResult extends BaseDataClass {
        public FlowInstance flowInstance;
        public Flow flow;

        public FlowInstanceResult(String errorMessage) {
            this.addErrorMessage(errorMessage);
        }

        public FlowInstanceResult(Flow flow) {
            this.flow = flow;
        }

        public FlowInstanceResult(Flow flow, FlowInstance flowInstance) {
            this.flow = flow;
            this.flowInstance = flowInstance;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowValidation extends BaseDataClass {
        public EnumsApi.FlowValidateStatus status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowListResult extends BaseDataClass {
        public Flow flow;
        public long currentFlowId;
    }
}
