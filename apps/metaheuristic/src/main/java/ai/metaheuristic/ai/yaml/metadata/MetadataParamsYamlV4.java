/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.api.data.BaseParams;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 6:09 PM
 */
@Data
@NoArgsConstructor
public class MetadataParamsYamlV4 implements BaseParams {

    public final int version=4;

    @Data
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class ProcessorSessionV4 {
        public String dispatcherCode;
        @Nullable
        public Long processorId;
        @Nullable
        public String sessionId;

        // key - code of Core, value - coreId
        public final LinkedHashMap<String, Long> cores = new LinkedHashMap<>();

        public final List<QuotaV4> quotas = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode(of={"taskId"})
    public static class QuotaV4 {
        public Long taskId;
        @Nullable
        public String tag;
        public int quota;
    }

    /**
     * key  - a code of dispatcher (i.e. normalized url of dispatcher)
     * value - ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml.ProcessorSession
     */
    @Nullable
    public LinkedHashMap<String, ProcessorSessionV4> processorSessions = new LinkedHashMap<>();
}