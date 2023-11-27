/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.List;

public class ProcessorData {

    public record ProcessorAndCoreParams(Long processorId, Long coreId, ProcessorStatusYaml psy, CoreStatusYaml csy) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkOperation {
        public Long processorId;
        public OperationStatusRest status;
    }

    @Data
    public static class BulkOperations {
        public List<BulkOperation> operations = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorId {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    public static class ProcessorWithSessionId {
        public Processor processor;
        public String sessionId;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ProcessorsResult extends BaseDataClass {
        public Slice<ProcessorStatus> items;
    }

    public record ProcessorCore(Long id, String code, boolean busy) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorStatus {
        public Processor processor;
        public boolean active;
        public boolean functionProblem;
        public boolean blacklisted;
        public String blacklistReason;
        public long lastSeen;
        public String ip;
        public String host;
        public final List<ProcessorCore> cores = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ProcessorResult extends BaseDataClass {
        public Processor processor;

        public ProcessorResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ProcessorResult(Processor processor) {
            this.processor = processor;
        }
    }

}
