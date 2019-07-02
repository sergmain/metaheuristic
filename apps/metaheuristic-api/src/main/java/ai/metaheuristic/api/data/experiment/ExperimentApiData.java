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
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.launchpad.Workbook;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/23/2019
 * Time: 11:55 AM
 */
public class ExperimentApiData {

    public static final PlotData EMPTY_PLOT_DATA = new PlotData();

    @Data
    @NoArgsConstructor
    public static class ExperimentData {
        public Long id;
        public Integer version;
        public Long workbookId;
        public String code;
        public String name;
        public String description;
        public int seed;
        public boolean isAllTaskProduced;
        public boolean isFeatureProduced;
        public long createdOn;
        public int numberOfTask;
        public List<ExperimentParamsYaml.HyperParam> hyperParams;
        public Map<String, Map<String, Integer>> hyperParamsAsMap;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamData {
        public String key;
        public String values;
        public Integer variants;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamsResult {
        public List<HyperParamData> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsEditResult extends BaseDataClass {
        public HyperParamsResult hyperParams;
        public ExperimentApiData.SimpleExperiment simpleExperiment;
        public ExperimentApiData.SnippetResult snippetResult;

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
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeatureData {
        public Long id;
        public Integer version;
        public String resourceCodes;
        public String checksumIdCodes;
        public int execStatus;
        public String execStatusAsString;
        public Long experimentId;
        public Double maxValue;

    }

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

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentResult extends BaseDataClass {
        public ExperimentData experiment;
        public String params;

        public ExperimentResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentResult(ExperimentData experiment, String params) {
            this.experiment = experiment;
            this.params = params;
        }
    }

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
            public String snippetCode;
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
        public Workbook workbook;
        public EnumsApi.WorkbookExecState workbookExecState;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamElement {
        String param;
        boolean isSelected;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamList {
        String key;
        public final List<HyperParamElement> list = new ArrayList<>();
        public boolean isSelectable() {
            return list.size()>1;
        }
    }

    @Data
    public static class ExperimentInfoResult {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeatureData> features;
        public Workbook workbook;
        public EnumsApi.WorkbookExecState workbookExecState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfoExtendedResult extends BaseDataClass {
        public ExperimentData experiment;
        public ExperimentInfoResult experimentInfo;

        public ExperimentInfoExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentSnippetResult  {
        public Long id;
        public Integer version;
        public String snippetCode;
        public String type;
        public long experimentId;
    }
    @Data
    public static class SnippetResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentSnippetResult> snippets = new ArrayList<>();

        public void sortSnippetsByOrder() {
//            snippets.sort(Comparator.comparingInt(ExperimentSnippetResult::getOrder));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperiment {
        public String name;
        public String description;
        public String code;
        public int seed;
        public long id;
    }
}
