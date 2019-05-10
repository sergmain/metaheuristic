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

import aiai.ai.launchpad.atlas.AtlasSimple;
import aiai.ai.launchpad.atlas.ExperimentStoredToAtlas;
import aiai.ai.launchpad.beans.Atlas;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.data.BaseDataClass;
import aiai.api.v1.data.TaskApiData;
import aiai.api.v1.launchpad.Workbook;
import aiai.ai.utils.SimpleSelectOption;
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

public class AtlasData {

    public static final PlotData EMPTY_PLOT_DATA = new PlotData();

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class PlotData extends BaseDataClass {
        public List<String> x = new ArrayList<>();
        public List<String> y = new ArrayList<>();
        public BigDecimal[][] z;

        public PlotData(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    public static class ExperimentInfo {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentStoredToAtlas.ExperimentFeatureOnShelf > features;
        public Workbook workbook;
        public EnumsApi.WorkbookExecState workbookExecState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfoExtended extends BaseDataClass {
        public Atlas atlas;
        public Experiment experiment;
        public ExperimentInfo experimentInfo;

        public ExperimentInfoExtended(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class AtlasExperiments extends BaseDataClass {
        public Slice<Atlas> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class AtlasSimpleExperiments extends BaseDataClass {
        public Slice<AtlasSimple> items;
    }

    @Data
    public static class MetricsResult {
        public final LinkedHashSet<String> metricNames = new LinkedHashSet<>();
        public final List<MetricElement> metrics = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricElement {
        public final List<BigDecimal> values = new ArrayList<>();
        public String params;

        @SuppressWarnings("Duplicates")
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
    public static class HyperParamResult {
        public final List<ExperimentData.HyperParamList> elements = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ConsoleResult extends BaseDataClass {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleConsoleOutput {
            public int exitCode;
            public boolean isOk;
            public String console;
        }
        public final List<ConsoleResult.SimpleConsoleOutput> items = new ArrayList<>();

        public ConsoleResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureExtendedResult extends BaseDataClass {
        public MetricsResult metricsResult;
        public HyperParamResult hyperParamResult;
        public TaskApiData.TasksResult tasksResult;
        public Experiment experiment;
        public ExperimentFeature  experimentFeature;
        public ConsoleResult consoleResult;

        public ExperimentFeatureExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }


}
