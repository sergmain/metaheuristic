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

package ai.metaheuristic.ai.yaml.atlas;

import ai.metaheuristic.ai.launchpad.experiment.ExperimentUtils;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Data
@NoArgsConstructor
public class AtlasParamsYamlWithCache {

    public AtlasParamsYaml atlasParams = null;

    // for caching
    private SourceCodeParamsYaml sourceCodeParamsYaml = null;
    private ExperimentParamsYaml experimentParamsYaml = null;
    private WorkbookParamsYaml workbookParamsYaml = null;

    public ExperimentParamsYaml.ExperimentFeature getFeature(Long featureId) {
        return getExperimentParamsYaml().processing.features.stream().filter(o -> Objects.equals(o.id, featureId)).findAny().orElse(null);
    }

    public ExperimentParamsYaml.ExperimentTaskFeature getExperimentTaskFeature(Long taskId) {
        return getExperimentParamsYaml().processing.taskFeatures
                .stream()
                .filter(o -> o.taskId.equals(taskId))
                .findFirst().orElse(null);
    }

    public Map<String, Map<String, Integer>> getHyperParamsAsMap(boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentParamsYaml.HyperParam hyperParam : getExperimentParamsYaml().experimentYaml.getHyperParams()) {
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

    public SourceCodeParamsYaml getSourceCodeParamsYaml() {
        if (sourceCodeParamsYaml ==null) {
            synchronized (this) {
                if (sourceCodeParamsYaml ==null) {
                    //noinspection UnnecessaryLocalVariable
                    SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(atlasParams.sourceCode.sourceCodeParams);
                    sourceCodeParamsYaml = ppy;
                }
            }
        }
        return sourceCodeParamsYaml;
    };

    public ExperimentParamsYaml getExperimentParamsYaml() {
        if (experimentParamsYaml==null) {
            synchronized (this) {
                if (experimentParamsYaml==null) {
                    //noinspection UnnecessaryLocalVariable
                    ExperimentParamsYaml epy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(atlasParams.experiment.experimentParams);
                    experimentParamsYaml = epy;
                }
            }
        }
        return experimentParamsYaml;
    };


    public WorkbookParamsYaml getWorkbookParamsYaml() {
        if (workbookParamsYaml==null) {
            synchronized (this) {
                if (workbookParamsYaml==null) {
                    //noinspection UnnecessaryLocalVariable
                    WorkbookParamsYaml wpy = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(atlasParams.execContext.execContextParams);
                    workbookParamsYaml = wpy;
                }
            }
        }
        return workbookParamsYaml;
    };

    public AtlasParamsYamlWithCache(AtlasParamsYaml atlasParams) {
        this.atlasParams = atlasParams;
    }
}
