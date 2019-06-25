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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 10:03 PM
 */
@Data
@NoArgsConstructor
public class ExperimentParamsYamlV1 {

    @Data
    public static class HyperParamV1 {
        public String key;
        public String values;
        public int variants;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentYamlV1 {
        public String name;
        public String description;
        public String code;

        public int seed = 42;
        public List<HyperParamV1> hyperParams = new ArrayList<>();

        public String fitSnippet;
        public String predictSnippet;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeatureV1 {

        public Long id;
        public String resourceCodes;
        public String checksumIdCodes;
        public int execStatus;
        public Long experimentId;
        public Double maxValue;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentTaskFeatureV1 {
        public Long id;
        public Long workbookId;
        public Long taskId;
        public Long featureId;
        public int taskType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentProcessingV1 {
        public boolean isAllTaskProduced = false;
        public boolean isFeatureProduced = false;

        public long createdOn;
        public int numberOfTask = 0;

        public List<ExperimentFeatureV1> features = new ArrayList<>();
        public List<ExperimentTaskFeatureV1> taskFeatures = new ArrayList<>();
    }

    public ExperimentYamlV1 yaml = new ExperimentYamlV1();
    public ExperimentProcessingV1 processing = new ExperimentProcessingV1();

}
