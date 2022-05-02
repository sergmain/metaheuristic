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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.*;

@Data
@NoArgsConstructor
public class MetadataParamsYaml implements BaseParams {

    public final int version=3;

    @Data
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class ProcessorSession {
        public String dispatcherCode;
        @Nullable
        public String processorId;
        @Nullable
        public String sessionId;

        // key - code of Core, value - coreId
        public final LinkedHashMap<String, Long> cores = new LinkedHashMap<>();

        public final List<Quota> quotas = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function {
        public EnumsApi.FunctionState state;
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

//procesorSessions:
//  http://localhost:8080:
//    dispatcherCode: localhost-8080
//    processorId: '3410'
//    sessionId: 4266a35f-290d-49b8-83cc-c75913598719-4f020c95-0283-455a-9467-0a76bbaba796
//    cores:
//      proc-01: '3410'
//      proc-02: '3410'

    /**
     * key  - a code of dispatcher (i.e. normalized url of dispatcher)
     * value - ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml.ProcessorSession
     */
    public final LinkedHashMap<String, ProcessorSession> processorSessions = new LinkedHashMap<>();
    public final List<Function> functions = new ArrayList<>();
    public final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
}