/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.yaml.series.SeriesParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
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
 * Date: 3/30/2021
 * Time: 3:25 PM
 */
public class SeriesData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SeriesResult extends BaseDataClass {
        public SimpleSeries series;

        public SeriesResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public SeriesResult(SimpleSeries series) {
            this.series = series;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SeriesesResult extends BaseDataClass {
        public Slice<SeriesResult> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleSeries {
        public Long id;
        public String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperimentResult {
        public Long id;
        public String name;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentResultList extends BaseDataClass{
        public List<SimpleExperimentResult> list;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SeriesDetail {
        public Map<String, String> hyperParams;
        public EnumsApi.Fitting fitting;
        public final SeriesParamsYaml.MetricValues metrics = new SeriesParamsYaml.MetricValues();
        public final List<String> variables = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ImportDetail {
        public ExperimentResultData.SimpleExperimentResult experimentResult;
        public boolean imported;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SeriesImportDetails extends BaseDataClass {
        public Long seriesId;
        public String seriesName;
        public final List<ImportDetail> importDetails = new ArrayList<>();

        public SeriesImportDetails(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SeriesDetails extends BaseDataClass {
        public Long seriesId;
        public String seriesName;
        public SeriesParamsYaml params;

        public final List<SeriesDetail> underFitting = new ArrayList<>();
        public final List<SeriesDetail> normalFitting = new ArrayList<>();
        public final List<SeriesDetail> overFitting = new ArrayList<>();

        public SeriesDetails(String errorMessage) {
            addErrorMessage(errorMessage);
        }

    }
}
