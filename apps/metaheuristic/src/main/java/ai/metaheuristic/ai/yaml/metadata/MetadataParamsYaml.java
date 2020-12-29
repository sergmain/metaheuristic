/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataParamsYaml implements BaseParams {

    public final int version=2;

    @Data
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class Core {
        public String coreId;
        public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class ProcessorState {
        public String dispatcherCode;
        public String processorId;
        public String sessionId;
        public final List<Core> cores = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Status {
        public Enums.FunctionState functionState;
        public String code;
        public String assetUrl;
        public EnumsApi.FunctionSourcing sourcing;
        public boolean verified;
    }

    public final LinkedHashMap<String, ProcessorState> processorStates = new LinkedHashMap<>();
    public final List<Status> statuses = new ArrayList<>();
    public final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
}
