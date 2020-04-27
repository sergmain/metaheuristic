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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ExperimentResultParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (execContext ==null) {
            throw new IllegalArgumentException("(execContext==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeatureV1 {

        public Long id;
        public List<String> variables;
        public int execStatus;
        public Long experimentId;
        public Double maxValue;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentTaskFeatureV1 {
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
    public static class ExecContextWithParamsV1 {
        public Long execContextId;
        public String execContextParams;
    }

    public long createdOn;
    public String name;
    public String description;
    public String code;

    public ExecContextWithParamsV1 execContext;
    public boolean maxValueCalculated = false;

    public int numberOfTask = 0;

    public final List<ExperimentApiData.HyperParam> hyperParams = new ArrayList<>();
    public final List<ExperimentFeatureV1> features = new ArrayList<>();
    public final List<ExperimentTaskFeatureV1> taskFeatures = new ArrayList<>();

}
