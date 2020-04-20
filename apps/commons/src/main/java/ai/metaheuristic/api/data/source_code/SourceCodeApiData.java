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
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.api.dispatcher.ExecContext;
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
    public static class SourceCodeStatus {
        public boolean isOk;
        public String error;
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResultComplex {
        public SourceCodeApiData.SourceCodeValidationResult sourceCodeValidationResult = new SourceCodeValidationResult(
                EnumsApi.SourceCodeValidateStatus.NOT_VALIDATED_YET_ERROR, "Not validated yet");
        public EnumsApi.TaskProducingStatus taskProducingStatus = EnumsApi.TaskProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public int numberOfTasks;

        public TaskProducingResultComplex(EnumsApi.TaskProducingStatus taskProducingStatus) {
            this.taskProducingStatus = taskProducingStatus;
        }

        public TaskProducingResultComplex(SourceCodeApiData.SourceCodeValidationResult sourceCodeValidationResult) {
            this.sourceCodeValidationResult = sourceCodeValidationResult;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class SourceCodesResult extends BaseDataClass {
        public Slice<SourceCode> items;
        public EnumsApi.DispatcherAssetMode assetMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeResult extends BaseDataClass {
        public SourceCode sourceCode;
        public String sourceCodeYamlAsStr;
        public EnumsApi.SourceCodeLang lang;
        public SourceCodeValidationResult status = new SourceCodeValidationResult(
                EnumsApi.SourceCodeValidateStatus.NOT_VERIFIED_YET, "Not verified yet");

        public SourceCodeResult(String errorMessage, SourceCodeValidationResult status) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SourceCodeResult(List<String> errorMessage, SourceCodeValidationResult status) {
            this.status = status;
            this.errorMessages = errorMessage;
        }

        public SourceCodeResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SourceCodeResult(SourceCode sourceCode, SourceCodeStoredParamsYaml storedParams) {
            this.sourceCode = sourceCode;
            this.lang = storedParams.lang;
            this.sourceCodeYamlAsStr = storedParams.source;
        }

        public SourceCodeResult(SourceCode sourceCode, EnumsApi.SourceCodeLang lang, String sourceCodeYamlAsStr) {
            this.sourceCode = sourceCode;
            this.lang = lang;
            this.sourceCodeYamlAsStr = sourceCodeYamlAsStr;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExecContextResult extends BaseDataClass {
        public ExecContext execContext;
        public SourceCode sourceCode;

        public ExecContextResult(List<String> errorMessages) {
            this.errorMessages = errorMessages;
        }
        public ExecContextResult(String errorMessage) {
            this.addErrorMessage(errorMessage);
        }

        public ExecContextResult(SourceCode sourceCode) {
            this.sourceCode = sourceCode;
        }

        public ExecContextResult(SourceCode sourceCode, ExecContext execContext) {
            this.sourceCode = sourceCode;
            this.execContext = execContext;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExecContextForDeletion extends BaseDataClass {
        public Long sourceCodeId;
        public Long execContextId;
        public String sourceCodeUid;
        public String execState;

        public ExecContextForDeletion(List<String> errorMessages) {
            this.errorMessages = errorMessages;
        }
        public ExecContextForDeletion(String errorMessage) {
            this.addErrorMessage(errorMessage);
        }

        public ExecContextForDeletion(Long sourceCodeId, Long execContextId, String sourceCodeUid, String execState) {
            this.sourceCodeId = sourceCodeId;
            this.execContextId = execContextId;
            this.sourceCodeUid = sourceCodeUid;
            this.execState = execState;
        }
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceCodeValidationResult {
        public EnumsApi.SourceCodeValidateStatus status;
        public String error;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeValidation extends BaseDataClass {
        public SourceCodeValidationResult status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeListResult extends BaseDataClass {
        public SourceCode sourceCode;
        public long currentSourceCodeId;
    }

    // !!! DO NOT CHANGE THIS CLASS UNDER ANY CIRCUMSTANCES
    @Data
    public static class SourceCodeInternalParamsYaml {
        public boolean archived;
    }

}
