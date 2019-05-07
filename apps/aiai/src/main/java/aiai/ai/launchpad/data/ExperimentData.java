/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.data;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.experiment.ExperimentTopLevelService;
import aiai.ai.utils.SimpleSelectOption;
import aiai.ai.utils.holders.BigDecimalHolder;
import aiai.ai.yaml.hyper_params.HyperParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ExperimentData {

    public static final PlotData EMPTY_PLOT_DATA = new PlotData();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamsResult {
        public List<ExperimentHyperParams> items = new ArrayList<>();

        public static HyperParamsResult getInstance(Experiment experiment) {
            HyperParamsResult r = new HyperParamsResult();
            r.items.addAll(experiment.getHyperParams());
            return r;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsEditResult extends BaseDataClass {
        public HyperParamsResult hyperParams;
        public SimpleExperiment simpleExperiment;
        public SnippetResult snippetResult;

        public ExperimentsEditResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsResult extends BaseDataClass {
        public Slice<Experiment> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricElement {
        public final List<BigDecimal> values = new ArrayList<>();
        public String params;

        public static int compare(MetricElement o2, MetricElement o1) {
            for (int i = 0; i < Math.min(o1.values.size(), o2.values.size()); i++) {
                final BigDecimal holder1 = o1.values.get(i);
                if (holder1 == null) {
                    return -1;
                }
                final BigDecimal holder2 = o2.values.get(i);
                if (holder2 == null) {
                    return -1;
                }
                int c = ObjectUtils.compare(holder1, holder2);
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(o1.values.size(), o2.values.size());        }
    }

    @Data
    public static class MetricsResult {
        public final LinkedHashSet<String> metricNames = new LinkedHashSet<>();
        public final List<MetricElement> metrics = new ArrayList<>();
    }

    @Data
    public static class HyperParamResult {
        public final List<HyperParamList> elements = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureExtendedResult extends BaseDataClass {
        public MetricsResult metricsResult;
        public HyperParamResult hyperParamResult;
        public TasksData.TasksResult tasksResult;
        public Experiment experiment;
        public ExperimentFeature  experimentFeature;
        public ExperimentData.ConsoleResult consoleResult;

        public ExperimentFeatureExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentResult extends BaseDataClass {
        public Experiment experiment;

        public ExperimentResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentResult(Experiment experiment) {
            this.experiment = experiment;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfo extends BaseDataClass {
        public Experiment experiment;
        public ExperimentExtendedResult experimentResult;

        public ExperimentInfo(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentInfo(Experiment experiment, ExperimentExtendedResult experimentResult) {
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
            public int exitCode;
            public boolean isOk;
            public String console;
        }
        public final List<SimpleConsoleOutput> items = new ArrayList<>();
    }

    @Data
    public static class ExperimentExtendedResult {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeature> features;
        public Workbook workbook;
        public Enums.WorkbookExecState workbookExecState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureProgress extends BaseDataClass {
        public TasksData.TasksResult tasksResult;
        public Experiment experiment;
        public ExperimentFeature experimentFeature;
        public ExperimentExtendedResult experimentResult;
        public ConsoleResult consoleResult;

        public ExperimentFeatureProgress(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentFeatureProgress(Experiment experiment, ExperimentExtendedResult experimentResult) {
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
        public List<ExperimentFeature> features;
        public Workbook workbook;
        public Enums.WorkbookExecState workbookExecState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfoExtendedResult extends BaseDataClass {
        public Experiment experiment;
        public ExperimentInfoResult experimentInfo;

        public ExperimentInfoExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    public static class SnippetResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentSnippet> snippets = new ArrayList<>();

        public void sortSnippetsByOrder() {
//            snippets.sort(Comparator.comparingInt(ExperimentSnippet::getOrder));
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

        public static SimpleExperiment to(Experiment e) {
            return new SimpleExperiment(e.getName(), e.getDescription(), e.getCode(), e.getSeed(), e.getId());
        }
    }
}
