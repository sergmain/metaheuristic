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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.SimpleSelectOption;
import ai.metaheuristic.api.dispatcher.ExecContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/23/2019
 * Time: 11:55 AM
 */
public class ExperimentApiData {

    /**
     * !!! DO NOT CHANGE THAT CLASS (HyperParam) UNDER ANY CIRCUMSTANCES !!!
     */
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
    public static class ExperimentData {
        public Long id;
        public Integer version;
        public Long execContextId;
        public String code;
        public String name;
        public String description;
        public boolean isAllTaskProduced;
        public boolean isFeatureProduced;
        public long createdOn;
        public int numberOfTask;
        public final List<HyperParam> hyperParams = new ArrayList<>();
        public final Map<String, Map<String, Integer>> hyperParamsAsMap = new HashMap<>();

        public int state;
        public String getExecState() {
            return EnumsApi.ExecContextState.from(state);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsEditResult extends BaseDataClass {
        public ExperimentApiData.SimpleExperiment simpleExperiment;

        public ExperimentsEditResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsResult extends BaseDataClass {
        public Slice<ExperimentResult> items;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeatureData {
        public Long id;
        public Integer version;
        public String variables;
        public String checksumIdCodes;
        public int execStatus;
        public String execStatusAsString;
        public Long experimentId;
        public Double maxValue;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentResult extends BaseDataClass {
        public ExperimentData experiment;

        public ExperimentResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentResult(ExperimentData experiment) {
            this.experiment = experiment;
        }
    }

    @Data
    public static class ExperimentInfoResult {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeatureData> features;
        public ExecContext execContext;
        public EnumsApi.ExecContextState execContextState;
    }

    @Data
    @AllArgsConstructor
    public static class ExperimentProgressResult {
        public final long count;
        public final int execState;
        public final String execStateAsStr;
        public final boolean isCompleted;
        public final boolean isResultReceived;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperiment {
        public String name;
        public String description;
        public String code;
        public long id;
    }
}
