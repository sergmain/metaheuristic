/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 6:32 PM
 */
public class TaskData {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceTaskResult {
        public EnumsApi.SourceCodeProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks=0;
        public List<Long> taskIds = new ArrayList<>();

        public ProduceTaskResult(EnumsApi.SourceCodeProducingStatus status) {
            this.status = status;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls=null;
        public final Map<String, String> mappingCodeToOriginalFilename = new HashMap<>();
        public EnumsApi.SourceCodeProducingStatus status = EnumsApi.SourceCodeProducingStatus.OK;

        public ResourcePools(List<SimpleVariableAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = EnumsApi.SourceCodeProducingStatus.INPUT_VARIABLE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o->
                collectedInputs.computeIfAbsent(o.variable, p -> new ArrayList<>()).add(o.id)
            );

            initialInputResourceCodes.forEach(o-> mappingCodeToOriginalFilename.put(o.id, o.originalFilename));

            inputStorageUrls = initialInputResourceCodes.stream()
                    .collect(Collectors.toMap(o -> o.id, o -> {
                        DataStorageParams p = o.getParams();
                        return new SourceCodeParamsYaml.Variable(p.sourcing, p.git, p.disk, p.storageType);
                    }));

        }

        public void clean() {
            collectedInputs.values().forEach(o-> o.forEach(inputStorageUrls::remove));
            collectedInputs.clear();
            mappingCodeToOriginalFilename.clear();
        }

        public void add(String outputType, List<String> outputResourceCodes) {
            if (outputResourceCodes!=null) {
                collectedInputs.computeIfAbsent(outputType, k -> new ArrayList<>()).addAll(outputResourceCodes);
            }
        }

        public void merge(ResourcePools metaPools) {
            metaPools.collectedInputs.forEach((key, value) -> collectedInputs.merge(
                    key, value, (o, o1) -> {o.addAll(o1); return o;} )
            );
            inputStorageUrls.putAll(metaPools.inputStorageUrls);
            mappingCodeToOriginalFilename.putAll((metaPools.mappingCodeToOriginalFilename));
        }
    }
}
