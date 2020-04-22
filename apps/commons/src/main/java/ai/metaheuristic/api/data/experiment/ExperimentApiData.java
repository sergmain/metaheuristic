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

/*
    public static final PlotData EMPTY_PLOT_DATA = new PlotData();
*/

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
    }

/*
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamData {
        public String key;
        public String values;
        public Integer variants;
    }
*/

/*
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamsResult {
        public List<HyperParamData> items = new ArrayList<>();
    }
*/

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsEditResult extends BaseDataClass {
//        public HyperParamsResult hyperParams;
        public ExperimentApiData.SimpleExperiment simpleExperiment;
//        public FunctionResult functionResult;

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

/*
    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureExtendedResult extends BaseDataClass {
        public MetricsResult metricsResult;
        public HyperParamResult hyperParamResult;
        public TaskApiData.TasksResult tasksResult;
        public ExperimentData experiment;
        public ExperimentFeatureData experimentFeature;
        public ExperimentApiData.ConsoleResult consoleResult;

        public ExperimentFeatureExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }
*/

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentResult extends BaseDataClass {
        public ExperimentData experiment;
//        public @Nullable String params;

        public ExperimentResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentResult(ExperimentData experiment) {
            this.experiment = experiment;
//            this.params = params;
        }
/*
        public ExperimentResult(@NonNull ExperimentData experiment, @Nullable String params) {
            this.experiment = experiment;
            this.params = params;
        }
*/
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

/*
    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfo extends BaseDataClass {
        public ExperimentData experiment;
        public ExperimentApiData.ExperimentExtendedResult experimentResult;

        public ExperimentInfo(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentInfo(ExperimentData experiment, ExperimentApiData.ExperimentExtendedResult experimentResult) {
            this.experiment = experiment;
            this.experimentResult = experimentResult;
        }
    }

    @Data
    public static class ConsoleResult {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleConsoleOutput {
            public String functionCode;
            public int exitCode;
            public boolean isOk;
            public String console;
        }
        public final List<SimpleConsoleOutput> items = new ArrayList<>();
    }

    @Data
    public static class ExperimentExtendedResult {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeatureData> features;
        public ExecContext execContext;
        public EnumsApi.ExecContextState execContextState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureProgress extends BaseDataClass {
        public TaskApiData.TasksResult tasksResult;
        public ExperimentData experiment;
        public ExperimentFeatureData experimentFeature;
        public ExperimentExtendedResult experimentResult;
        public ConsoleResult consoleResult;

        public ExperimentFeatureProgress(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentFeatureProgress(ExperimentData experiment, ExperimentExtendedResult experimentResult) {
            this.experiment = experiment;
            this.experimentResult = experimentResult;
        }
    }

    @Data
    @NoArgsConstructor
    public static class PlotData {
        public List<String> x = new ArrayList<>();
        public List<String> y = new ArrayList<>();
        public BigDecimal[][] z;
    }
*/

/*
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricElement implements BaseMetricElement {
        public final List<BigDecimal> values = new ArrayList<>();
        public String params;
    }

    @Data
    public static class MetricsResult {
        public final LinkedHashSet<String> metricNames = new LinkedHashSet<>();
        public final List<MetricElement> metrics = new ArrayList<>();
    }

    @Data
    public static class HyperParamResult {
        public final List<ExperimentApiData.HyperParamList> elements = new ArrayList<>();

//        *
//         * for plotting we need at least 2 HyperParams to be selected.
//         * in case when there is only one list ov values of params
//         * we will use all HyperParams for axises
        public boolean useAllHyperParamsInPlot() {
            int count=0;
            for (ExperimentApiData.HyperParamList element : elements) {
                if (element.list.size()>1) {
                    count++;
                }
            }
            return count<2;
        }
    }
*/

/*
    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfoExtendedResult extends BaseDataClass {
        public ExperimentData experiment;
        public ExperimentInfoResult experimentInfo;
        public List<ExperimentProgressResult> progress;

        public ExperimentInfoExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }
*/
/*

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentFunctionResult {
        public Long id;
        public Integer version;
        public String functionCode;
        public String type;
        public long experimentId;
    }

    @Data
    public static class FunctionResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentFunctionResult> functions = new ArrayList<>();

        public void sortFunctionsByOrder() {
//            functions.sort(Comparator.comparingInt(ExperimentFunctionResult::getOrder));
        }
    }
*/

}
