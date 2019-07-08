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

package ai.metaheuristic.api.data.experiment;

import ai.metaheuristic.api.data.BaseParams;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 10:03 PM
 */
@Data
@NoArgsConstructor
public class ExperimentParamsYaml implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParam {
        public String key;
        public String values;
        public Integer variants;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentYaml {
        public String name;
        public String description;
        public String code;

        public int seed = 42;
        public List<HyperParam> hyperParams = new ArrayList<>();

        public String fitSnippet;
        public String predictSnippet;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeature {

        public Long id;
        public String resourceCodes;
        public String checksumIdCodes;
        public int execStatus;
        public Long experimentId;
        public Double maxValue;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentTaskFeature {
        public Long id;
        public Long workbookId;
        public Long taskId;
        public Long featureId;
        public int taskType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentProcessing {
        public boolean isAllTaskProduced = false;
        public boolean isFeatureProduced = false;

        public int numberOfTask = 0;

        public List<ExperimentFeature> features = new ArrayList<>();
        public List<ExperimentTaskFeature> taskFeatures = new ArrayList<>();
    }

    public final int version=1;
    public long createdOn;
    public ExperimentYaml experimentYaml = new ExperimentYaml();
    public ExperimentProcessing processing = new ExperimentProcessing();


    @JsonIgnore
    public List<String> getSnippetCodes() {
        final List<String> snippetCodes = new ArrayList<>();

        if (experimentYaml.fitSnippet != null && !experimentYaml.fitSnippet.isBlank()) {
            snippetCodes.add(experimentYaml.fitSnippet);
        }
        if (experimentYaml.predictSnippet != null && !experimentYaml.predictSnippet.isBlank()) {
            snippetCodes.add(experimentYaml.predictSnippet);
        }
        return snippetCodes;
    }

    @JsonIgnore
    public ExperimentParamsYaml.ExperimentFeature getFeature(Long featureId) {
        //noinspection UnnecessaryLocalVariable
        ExperimentParamsYaml.ExperimentFeature feature = processing.features
                .stream().filter(o -> o.id.equals(featureId)).findFirst().orElse(null);

        return feature;
    }

    @JsonIgnore
    public List<ExperimentParamsYaml.ExperimentTaskFeature> getTaskFeatures(Long featureId) {
        //noinspection UnnecessaryLocalVariable
        List<ExperimentParamsYaml.ExperimentTaskFeature> taskFeatures = processing.taskFeatures
                .stream().filter(o -> o.id.equals(featureId)).collect(Collectors.toList());

        return taskFeatures;
    }

    @JsonIgnore
    public List<Long> getTaskFeatureIds(Long featureId) {
        //noinspection UnnecessaryLocalVariable
        List<Long> ids = processing.taskFeatures
                .stream().filter(o -> o.featureId.equals(featureId)).mapToLong(o->o.id).boxed().collect(Collectors.toList());
        return ids;
    }


}
