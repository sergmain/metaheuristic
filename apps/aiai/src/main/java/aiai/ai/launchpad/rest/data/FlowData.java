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

package aiai.ai.launchpad.rest.data;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.HashMap;
import java.util.Map;

public class FlowData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class FlowsResultRest extends BaseClassRest {
        public Slice<Flow> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowResultRest extends BaseClassRest {
        public Flow flow;
        public Enums.FlowValidateStatus status = Enums.FlowValidateStatus.NOT_VERIFIED_YET;

        public FlowResultRest(String errorMessage, Enums.FlowValidateStatus status) {
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public FlowResultRest(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public FlowResultRest(Flow flow) {
            this.flow = flow;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class FlowInstancesResultRest extends BaseClassRest {
        public Slice<FlowInstance> instances;
        public long currentFlowId;
        public Map<Long, Flow> flows = new HashMap<>();

    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowInstanceResultRest extends BaseClassRest {
        public FlowInstance flowInstance;
        public Flow flow;

        public FlowInstanceResultRest(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public FlowInstanceResultRest(Flow flow) {
            this.flow = flow;
        }

        public FlowInstanceResultRest(Flow flow, FlowInstance flowInstance) {
            this.flow = flow;
            this.flowInstance = flowInstance;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FlowValidation extends BaseClassRest{
        public Enums.FlowValidateStatus status;
    }
}
