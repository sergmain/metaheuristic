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

package ai.metaheuristic.ai.mhbp.data;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 7:08 PM
 */
public class ScenarioData {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApiUid {
        public Long id;
        public String uid;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScenarioUidsForAccount extends BaseDataClass {
        public List<ApiUid> apis;
    }

    @RequiredArgsConstructor
    public static class ScenarioGroupsResult extends BaseDataClass {
        public final Slice<SimpleScenarioGroup> scenarioGroups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ScenariosResult extends BaseDataClass {
        public Page<SimpleScenario> scenarios;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleScenarioGroup {
        public long scenarioGroupId;
        public long createdOn;
        public String name;
        public String description;

        public SimpleScenarioGroup(ScenarioGroup sg) {
            this.scenarioGroupId = sg.id;
            this.createdOn = sg.createdOn;
            this.name = sg.name;
            this.description = sg.description;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleScenarioStep {
        public long scenarioId;
        public String uuid;
        @Nullable
        public String parentUuid;
        public long apiId;
        public String apiCode;
        public String name;
        public String prompt;
        public String r;

        public String resultCode;
        public SimpleScenarioStep[] steps;

        public SimpleScenarioStep(Long scenarioId, ApiUid apiUid, ScenarioParams.Step step) {
            this.scenarioId = scenarioId;
            this.uuid = step.uuid;
            this.parentUuid = step.parentUuid;
            this.apiId = apiUid.id;
            this.apiCode = apiUid.uid;
            this.name = step.name;
            this.prompt = step.p;
            this.r = step.r;
            this.resultCode = step.resultCode;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SimpleScenarioSteps extends BaseDataClass {
        public List<SimpleScenarioStep> steps;
    }

}

