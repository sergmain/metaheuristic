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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 1:33 AM
 */
@Data
@NoArgsConstructor
public class MetadataParamsYamlV2 implements BaseParams {

    public final int version = 2;

    @Data
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class ProcessorStateV2 {
        public String dispatcherCode;
        public String processorId;
        public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @ToString
    public static class ProcessorV2 {
        // key is url of dispatcher
        public final Map<String, ProcessorStateV2> states = new LinkedHashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatusV2 {
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
    public static class QuotaV2 {
        public Long taskId;
        @Nullable
        public String tag;
        public int quota;
    }

    @Data
    public static class QuotasV2 {
        public final List<QuotaV2> quotas = new ArrayList<>();
    }

    // key is a code of processor which is configured in env.yaml
    public final LinkedHashMap<String, ProcessorV2> processors = new LinkedHashMap<>();

    public final List<StatusV2> statuses = new ArrayList<>();
    public final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    // key is url of dispatcher
    public final LinkedHashMap<String, QuotasV2> quotas = new LinkedHashMap<>();
}