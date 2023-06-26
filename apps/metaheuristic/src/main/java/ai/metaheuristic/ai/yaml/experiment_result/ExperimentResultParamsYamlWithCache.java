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

package ai.metaheuristic.ai.yaml.experiment_result;

import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Data
@NoArgsConstructor
public class ExperimentResultParamsYamlWithCache {

    public ExperimentResultParams experimentResult = null;

    // for caching
    private ExecContextParamsYaml execContextParamsYaml = null;

    public ExperimentResultParamsYamlWithCache(ExperimentResultParams experimentResult) {
        this.experimentResult = experimentResult;
    }

    @Nullable
    public ExperimentResultParams.ExperimentFeature getFeature(Long featureId) {
        return experimentResult.features.stream().filter(o -> Objects.equals(o.id, featureId)).findFirst().orElse(null);
    }

    @Nullable
    public ExperimentResultParams.ExperimentTaskFeature getExperimentTaskFeature(Long taskId) {
        return experimentResult.taskFeatures
                .stream()
                .filter(o -> o.taskId.equals(taskId))
                .findFirst().orElse(null);
    }

    public Map<String, Map<String, Integer>> getHyperParamsAsMap(boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentApiData.HyperParam hyperParam : experimentResult.hyperParams) {
            InlineVariableUtils.NumberOfVariants ofVariants = InlineVariableUtils.getNumberOfVariants(hyperParam.getValues() );
            Map<String, Integer> map = new LinkedHashMap<>();
            paramByIndex.put(hyperParam.getKey(), map);
            for (int i = 0; i <ofVariants.values.size(); i++) {
                String value = ofVariants.values.get(i);
                map.put(isFull ? hyperParam.getKey()+'-'+value : value , i);
            }
        }
        return paramByIndex;
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ExecContextParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecContextParamsYaml parseParams() {
        ExecContextParamsYaml temp = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.execContext.execContextParams);
        ExecContextParamsYaml ecpy = temp==null ? new ExecContextParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExecContextParamsYaml getExecContextParamsYaml() {
        return paramsLocked.get();
    }

}
