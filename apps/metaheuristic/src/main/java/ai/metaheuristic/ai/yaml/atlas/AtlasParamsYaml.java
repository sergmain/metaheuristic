/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.atlas;

import ai.metaheuristic.ai.launchpad.experiment.ExperimentUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtilsV5;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYamlV1;
import ai.metaheuristic.api.data.task.TaskParamsYamlV2;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYamlV2;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.*;

@Data
@NoArgsConstructor
public class AtlasParamsYaml {

    @JsonIgnore
    public ExperimentParamsYamlV1.ExperimentFeatureV1 getFeature(Long featureId) {
        return experimentParams.processing.features.stream().filter(o -> Objects.equals(o.id, featureId)).findAny().orElse(null);
    }

    @JsonIgnore
    public TaskOnShelfV1 getTask(Long taskId) {
        return tasks.stream().filter(o -> Objects.equals(o.taskId, taskId)).findAny().orElse(null);
    }

    @JsonIgnore
    public ExperimentParamsYamlV1.ExperimentTaskFeatureV1 getExperimentTaskFeature(Long taskId) {
        return experimentParams.processing.taskFeatures
                .stream()
                .filter(o -> o.taskId.equals(taskId))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("Duplicates")
    @JsonIgnore
    public Map<String, Map<String, Integer>> getHyperParamsAsMap(boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentParamsYamlV1.HyperParamV1 hyperParam : experimentParams.experimentYaml.getHyperParams()) {
            ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues() );
            Map<String, Integer> map = new LinkedHashMap<>();
            paramByIndex.put(hyperParam.getKey(), map);
            for (int i = 0; i <ofVariants.values.size(); i++) {
                String value = ofVariants.values.get(i);


                map.put(isFull ? hyperParam.getKey()+'-'+value : value , i);
            }
        }
        return paramByIndex;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class PlanOnShelf {
        public PlanParamsYamlUtilsV5 ppy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class WorkbookOnShelf {
        public WorkbookParamsYamlV2 workbookParams;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TaskOnShelfV1 {
        public Long taskId;
        public TaskParamsYamlV2 taskParams;
    }

    public PlanParamsYamlUtilsV5 planParams;
    public WorkbookParamsYamlV2 workbookParams;
    public ExperimentParamsYamlV1 experimentParams;
    public List<TaskOnShelfV1> tasks = new ArrayList<>();

    public AtlasParamsYaml(
            PlanParamsYamlUtilsV5 planParams, WorkbookParamsYamlV2 workbookParams,
            ExperimentParamsYamlV1 experimentParams, List<TaskOnShelfV1> tasks) {
        this.planParams = planParams;
        this.workbookParams = workbookParams;

        this.experimentParams = experimentParams;
        this.tasks = tasks;
    }
}
