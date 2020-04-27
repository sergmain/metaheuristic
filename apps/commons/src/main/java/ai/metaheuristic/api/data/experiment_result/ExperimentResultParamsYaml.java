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

package ai.metaheuristic.api.data.experiment_result;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class ExperimentResultParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (execContext==null) {
            throw new CheckIntegrityFailedException("(execContext==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentFeature {
        public Long id;
        public List<String> variables;
        public int execStatus;
        public Long experimentId;
        public Double maxValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentTaskFeature {
        public Long id;
        public Long execContextId;
        public Long taskId;
        public Long featureId;
        public int taskType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ExecContextWithParams {
        public Long execContextId;
        // String form of ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml
        public String execContextParams;
    }

    public long createdOn;
    public String name;
    public String description;
    public String code;

    public ExecContextWithParams execContext;
    public boolean maxValueCalculated = false;

    public int numberOfTask = 0;

    public final List<ExperimentApiData.HyperParam> hyperParams = new ArrayList<>();
    public final List<ExperimentFeature> features = new ArrayList<>();
    public final List<ExperimentTaskFeature> taskFeatures = new ArrayList<>();

    @Nullable
    @JsonIgnore
    public ExperimentFeature getFeature(Long featureId) {
        ExperimentFeature feature = features
                .stream().filter(o -> o.id.equals(featureId)).findFirst().orElse(null);

        return feature;
    }

    @JsonIgnore
    public List<ExperimentTaskFeature> getTaskFeatures(Long featureId) {
        List<ExperimentTaskFeature> result = taskFeatures
                .stream().filter(o -> o.id.equals(featureId)).collect(Collectors.toList());

        return result;
    }

    @JsonIgnore
    public List<Long> getTaskFeatureIds(Long featureId) {
        List<Long> ids = taskFeatures
                .stream().filter(o -> o.featureId.equals(featureId)).mapToLong(o->o.taskId).boxed().collect(Collectors.toList());
        return ids;
    }


}
