/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.yaml.metadata;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.*;

@Data
@NoArgsConstructor
public class MetadataParamsYaml implements BaseParams {

    public final int version=2;

    @Data
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class ProcessorState {
        public String dispatcherCode;
        @Nullable
        public String processorId;
        @Nullable
        public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @ToString
    public static class Processor {
        // key is url of dispatcher
        public final LinkedHashMap<String, ProcessorState> states = new LinkedHashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Status {
        public Enums.FunctionState functionState;
        public String code;
        public String assetManagerUrl;
        public EnumsApi.FunctionSourcing sourcing;

        public EnumsApi.ChecksumState checksum = EnumsApi.ChecksumState.not_yet;
        public EnumsApi.SignatureState signature = EnumsApi.SignatureState.not_yet;

        public final Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode(of={"taskId"})
    public static class Quota {
        public Long taskId;
        @Nullable
        public String tag;
        public int quota;
    }

    @Data
    public static class Quotas {
        public final List<Quota> quotas = new ArrayList<>();
    }

    /**
     * key  - a code of processor which is configured in env.yaml
     * value - ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml.Processor
     */
    public final LinkedHashMap<String, Processor> processors = new LinkedHashMap<>();

    public final List<Status> statuses = new ArrayList<>();
    public final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    // key is url of dispatcher
    public final LinkedHashMap<String, Quotas> quotas = new LinkedHashMap<>();
}
