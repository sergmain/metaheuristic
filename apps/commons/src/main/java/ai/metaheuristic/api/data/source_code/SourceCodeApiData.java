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

package ai.metaheuristic.api.data.source_code;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.*;

public class SourceCodeApiData {

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
        public EnumsApi.SourceCodeProducingStatus sourceCodeProducingStatus;
        public EnumsApi.SourceCodeValidateStatus sourceCodeValidateStatus;
        public int numberOfTasks = 0;
        public Long workbookId = null;

        public TaskProducingResult(
                List<String> errorMessages, EnumsApi.SourceCodeValidateStatus sourceCodeValidateStatus,
                EnumsApi.SourceCodeProducingStatus sourceCodeProducingStatus,
                Long workbookId ) {
            this.workbookId = workbookId;
            this.errorMessages = errorMessages;
            this.sourceCodeValidateStatus = sourceCodeValidateStatus;
            this.sourceCodeProducingStatus = sourceCodeProducingStatus;
        }
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResultComplex {
        public EnumsApi.SourceCodeValidateStatus sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.NOT_VALIDATED_YET_ERROR;
        public EnumsApi.SourceCodeProducingStatus sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml;
        public ExecContext execContext;
        public int numberOfTasks;

        public EnumsApi.TaskProducingStatus getStatus() {
            if (sourceCodeValidateStatus != EnumsApi.SourceCodeValidateStatus.OK) {
                return EnumsApi.TaskProducingStatus.VERIFY_ERROR;
            }
            if (sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
                return EnumsApi.TaskProducingStatus.PRODUCING_ERROR;
            }
            return EnumsApi.TaskProducingStatus.OK;
        }

        public TaskProducingResultComplex(EnumsApi.SourceCodeProducingStatus sourceCodeProducingStatus) {
            this.sourceCodeProducingStatus = sourceCodeProducingStatus;
        }

        public TaskProducingResultComplex(EnumsApi.SourceCodeValidateStatus sourceCodeValidateStatus) {
            this.sourceCodeValidateStatus = sourceCodeValidateStatus;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class SourceCodesResult extends BaseDataClass {
        public Slice<SourceCode> items;
        public EnumsApi.LaunchpadAssetMode assetMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeResult extends BaseDataClass {
        public SourceCode sourceCode;
        public String sourceCodeYamlAsStr;
        public EnumsApi.SourceCodeLang lang;
        public EnumsApi.SourceCodeValidateStatus status = EnumsApi.SourceCodeValidateStatus.NOT_VERIFIED_YET;

        public SourceCodeResult(String errorMessage, EnumsApi.SourceCodeValidateStatus status) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SourceCodeResult(List<String> errorMessage, EnumsApi.SourceCodeValidateStatus status) {
            this.status = status;
            this.errorMessages = errorMessage;
        }

        public SourceCodeResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SourceCodeResult(SourceCode sourceCode, SourceCodeParamsYaml.Origin origin) {
            this.sourceCode = sourceCode;
            this.sourceCodeYamlAsStr = origin.source;
            this.lang = origin.lang;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class WorkbooksResult extends BaseDataClass {
        public Slice<ExecContext> instances;
        public long currentPlanId;
        public Map<Long, SourceCode> plans = new HashMap<>();

    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class WorkbookResult extends BaseDataClass {
        public ExecContext execContext;
        public SourceCode sourceCode;

        public WorkbookResult(String errorMessage) {
            this.addErrorMessage(errorMessage);
        }

        public WorkbookResult(SourceCode sourceCode) {
            this.sourceCode = sourceCode;
        }

        public WorkbookResult(SourceCode sourceCode, ExecContext execContext) {
            this.sourceCode = sourceCode;
            this.execContext = execContext;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeValidation extends BaseDataClass {
        public EnumsApi.SourceCodeValidateStatus status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class PlanListResult extends BaseDataClass {
        public SourceCode sourceCode;
        public long currentPlanId;
    }

    // !!! DO NOT CHANGE THIS CLASS UNDER ANY CIRCUMSTANCES
    @Data
    public static class PlanInternalParamsYaml {
        public boolean archived;
    }

}
