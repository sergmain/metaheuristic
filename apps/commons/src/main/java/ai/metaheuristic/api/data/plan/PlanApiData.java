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

package ai.metaheuristic.api.data.plan;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.*;

public class PlanApiData {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PlanStatus {
        public boolean isOk;
        public String error;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskProducingResult extends BaseDataClass {
        public EnumsApi.PlanProducingStatus planProducingStatus;
        public EnumsApi.PlanValidateStatus planValidateStatus;
        public int numberOfTasks = 0;
        public Long workbookId = null;

        public TaskProducingResult(
                List<String> errorMessages, EnumsApi.PlanValidateStatus planValidateStatus,
                EnumsApi.PlanProducingStatus planProducingStatus,
                Long workbookId ) {
            this.workbookId = workbookId;
            this.errorMessages = errorMessages;
            this.planValidateStatus = planValidateStatus;
            this.planProducingStatus = planProducingStatus;
        }
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResultComplex {
        public EnumsApi.PlanValidateStatus planValidateStatus = EnumsApi.PlanValidateStatus.NOT_VALIDATED_YET_ERROR;
        public EnumsApi.PlanProducingStatus planProducingStatus = EnumsApi.PlanProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public PlanParamsYaml.PlanYaml planYaml;
        public Workbook workbook;
        public int numberOfTasks;

        public EnumsApi.TaskProducingStatus getStatus() {
            if (planValidateStatus != EnumsApi.PlanValidateStatus.OK) {
                return EnumsApi.TaskProducingStatus.VERIFY_ERROR;
            }
            if (planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
                return EnumsApi.TaskProducingStatus.PRODUCING_ERROR;
            }
            return EnumsApi.TaskProducingStatus.OK;
        }

        public TaskProducingResultComplex(EnumsApi.PlanProducingStatus planProducingStatus) {
            this.planProducingStatus = planProducingStatus;
        }

        public TaskProducingResultComplex(EnumsApi.PlanValidateStatus planValidateStatus) {
            this.planValidateStatus = planValidateStatus;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class PlansResult extends BaseDataClass {
        public Slice<Plan> items;
        public EnumsApi.LaunchpadAssetMode assetMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class PlanResult extends BaseDataClass {
        public Plan plan;
        public String planYamlAsStr;
        public EnumsApi.SourceCodeLang lang;
        public EnumsApi.PlanValidateStatus status = EnumsApi.PlanValidateStatus.NOT_VERIFIED_YET;

        public PlanResult(String errorMessage, EnumsApi.PlanValidateStatus status) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public PlanResult(List<String> errorMessage, EnumsApi.PlanValidateStatus status) {
            this.status = status;
            this.errorMessages = errorMessage;
        }

        public PlanResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public PlanResult(Plan plan, PlanParamsYaml.Origin origin) {
            this.plan = plan;
            this.planYamlAsStr = origin.source;
            this.lang = origin.lang;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class WorkbooksResult extends BaseDataClass {
        public Slice<Workbook> instances;
        public long currentPlanId;
        public Map<Long, Plan> plans = new HashMap<>();

    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class WorkbookResult extends BaseDataClass {
        public Workbook workbook;
        public Plan plan;

        public WorkbookResult(String errorMessage) {
            this.addErrorMessage(errorMessage);
        }

        public WorkbookResult(Plan plan) {
            this.plan = plan;
        }

        public WorkbookResult(Plan plan, Workbook workbook) {
            this.plan = plan;
            this.workbook = workbook;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class PlanValidation extends BaseDataClass {
        public EnumsApi.PlanValidateStatus status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class PlanListResult extends BaseDataClass {
        public Plan plan;
        public long currentPlanId;
    }

    // !!! DO NOT CHANGE THIS CLASS UNDER ANY CIRCUMSTANCES
    @Data
    public static class PlanInternalParamsYaml {
        public boolean archived;
    }

}
