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
import aiai.ai.launchpad.atlas.AtlasSimple;
import aiai.ai.launchpad.atlas.ExperimentStoredToAtlas;
import aiai.ai.launchpad.beans.Atlas;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.utils.SimpleSelectOption;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.List;

public class AtlasData {

    @Data
    public static class ExperimentInfo {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentStoredToAtlas.ExperimentFeatureOnShelf > features;
        public FlowInstance flowInstance;
        public Enums.FlowInstanceExecState flowInstanceExecState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfoExtended extends BaseDataClass {
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


}
