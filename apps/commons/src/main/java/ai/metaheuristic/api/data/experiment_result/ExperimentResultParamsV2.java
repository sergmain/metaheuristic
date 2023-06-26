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

package ai.metaheuristic.api.data.experiment_result;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.*;

@Data
@NoArgsConstructor
public class ExperimentResultParamsV2 implements BaseParams {

    public final int version = 2;

    @Override
    public boolean checkIntegrity() {
        if (execContext ==null) {
            throw new IllegalArgumentException("(execContext==null)");
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MetricValuesV2 {
        // key - name of metric, value - value of metric
        public LinkedHashMap<String, BigDecimal> values = new LinkedHashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExperimentPartV2 {
        public String taskContextId;
        @Nullable
        public Map<String, String> hyperParams;
        @Nullable
        public EnumsApi.Fitting fitting;
        @Nullable
        public MetricValuesV2 metrics;
        @Nullable
        public List<String> featureVariables;

        public ExperimentPartV2(String taskContextId) {
            this.taskContextId = taskContextId;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeatureV2 {
        public Long id;
        public List<String> variables;
        public int execStatus;
        public Long experimentId;
        public final Map<String, Double> maxValues = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentTaskFeatureV2 {
        public Long id;
        public Long execContextId;
        public Long taskId;
        public Long featureId;
        public int taskType;
        public MetricValuesV2 metrics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ExecContextWithParamsV2 {
        public Long execContextId;
        public String execContextParams;
    }

    public long createdOn;
    public String name;
    public String description;
    public String code;

    public ExecContextWithParamsV2 execContext;
    public boolean maxValueCalculated = false;

    public int numberOfTask = 0;

    public final List<ExperimentApiData.HyperParam> hyperParams = new ArrayList<>();
    public final List<ExperimentFeatureV2> features = new ArrayList<>();
    public final List<ExperimentTaskFeatureV2> taskFeatures = new ArrayList<>();
    public final List<ExperimentPartV2> parts = new ArrayList<>();

}
