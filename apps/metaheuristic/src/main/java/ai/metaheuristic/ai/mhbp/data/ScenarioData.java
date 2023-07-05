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

import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 7:08 PM
 */
public class ScenarioData {

    @Data
    public static class StepVariable {
        public String name;
        public String value;
    }

    @Data
    public static class StepEvaluation {
        public String uuid;
        public String prompt;
        public List<StepVariable> variables;
    }

    @Data
    public static class StepEvaluationResult {
        public long scenarioId;
        public String uuid;
        public String result;
        public String rawrResult;
        public String prompt;
        @Nullable
        public String error;

        public StepEvaluationResult(long scenarioId, String uuid) {
            this.scenarioId = scenarioId;
            this.uuid = uuid;
        }

        public void update(ChatData.ChatPrompt chatPrompt) {
            this.prompt = chatPrompt.prompt;
            this.result = chatPrompt.result;
            this.rawrResult = chatPrompt.raw;
            this.error = chatPrompt.error;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class PreparedStep extends BaseDataClass {
        public final String uuid;
        @Nullable
        public final Set<String> inputs;

        public PreparedStep(String uuid, @Nullable Set<String> inputs, @Nullable List<String> error) {
            this.uuid = uuid;
            this.inputs = inputs;
            if (error!=null && !error.isEmpty()) {
                super.addErrorMessages(error);
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class StepInput {
        public String name;
        public String value;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvaluateStep {
        public String uuid;
        public final List<StepInput> inputs = new ArrayList<>();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class StepEvaluationPrepareResult {
        public long scenarioId;
        public String uuid;
        public final List<StepInput> inputs = new ArrayList<>();
        @Nullable
        public String error;

        public StepEvaluationPrepareResult(long scenarioId, String uuid) {
            this.scenarioId = scenarioId;
            this.uuid = uuid;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InternalFunction {
        public String code;
        public String translate;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScenarioUidsForAccount extends BaseDataClass {
        public List<InternalFunction> functions;
        public List<ApiData.ApiUid> apis;
        public List<String> aggregateTypes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ScenariosResult extends BaseDataClass {
        public Page<SimpleScenario> scenarios;
    }

    @RequiredArgsConstructor
    public static class ScenarioGroupsResult extends BaseDataClass {
        public final Slice<SimpleScenarioGroup> scenarioGroups;
    }

    @RequiredArgsConstructor
    public static class ScenarioGroupsAllResult extends BaseDataClass {
        public final List<SimpleScenarioGroup> scenarioGroups;
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
    public static class SimpleScenarioStep implements CollectionUtils.TreeUtils.TreeItem<String> {
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

        // this field is for mh.acceptance-test function only. in future, it'll be changed to meta, maybe.
        @Nullable
        public String expected;

        @Nullable
        public String functionCode;

        @Nullable
        public String aggregateType;

        public boolean isCachable = false;

        @Nullable
        public SimpleScenarioStep[] steps;

        public SimpleScenarioStep(Long scenarioId, ApiData.ApiUid apiUid, ScenarioParams.Step step, @Nullable String functionCode, @Nullable String aggregateType ) {
            this.scenarioId = scenarioId;
            this.uuid = step.uuid;
            this.parentUuid = step.parentUuid;
            this.name = step.name;
            this.prompt = step.p;
            this.r = step.r;
            this.expected = step.expected;
            this.resultCode = step.resultCode;
            this.isCachable = step.isCachable;

            this.apiId = apiUid.id;
            this.apiCode = apiUid.uid;

            this.functionCode = functionCode;
            this.aggregateType = aggregateType;
        }

        public SimpleScenarioStep(String uuid) {
            this.uuid = uuid;
        }

        @JsonIgnore
        @Override
        public String getTopId() {
            return parentUuid;
        }

        @JsonIgnore
        @Override
        public String getId() {
            return uuid;
        }

        @JsonIgnore
        @Nullable
        @Override
        public List<CollectionUtils.TreeUtils.TreeItem<String>> getSubTree() {
            return steps==null ? null : Arrays.asList(steps);
        }

        @JsonIgnore
        @Override
        public void setSubTree(@Nullable List<CollectionUtils.TreeUtils.TreeItem<String>> list) {
            this.steps = list==null ? null : list.toArray(new SimpleScenarioStep[0]);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this==o) {
                return true;
            }
            if (o==null || getClass()!=o.getClass()) {
                return false;
            }

            SimpleScenarioStep that = (SimpleScenarioStep) o;

            if (!uuid.equals(that.uuid)) {
                return false;
            }
            return Objects.equals(parentUuid, that.parentUuid);
        }

        @Override
        public int hashCode() {
            int result = uuid.hashCode();
            result = 31 * result + (parentUuid!=null ? parentUuid.hashCode() : 0);
            return result;
        }

        /*
        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            SimpleScenarioStep that = (SimpleScenarioStep) o;

            if (!this.uuid.equals(that.uuid)) {
                return false;
            }
            if (this.parentUuid != null ? !this.parentUuid.equals(that.parentUuid) : that.parentUuid != null) {
                return false;
            }

            return true;
        }
*/
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleScenarioInfo {
        public String name;
        public String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SimpleScenarioSteps extends BaseDataClass {
        @Nullable
        public SimpleScenarioInfo scenarioInfo;
        public List<SimpleScenarioStep> steps;
    }

}

