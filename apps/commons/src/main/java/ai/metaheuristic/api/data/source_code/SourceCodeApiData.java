/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

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

        public TaskProducingResultComplex(EnumsApi.TaskProducingStatus taskProducingStatus) {
            this.taskProducingStatus = taskProducingStatus;
        }

        public TaskProducingResultComplex(SourceCodeApiData.SourceCodeValidationResult sourceCodeValidationResult) {
            this.sourceCodeValidationResult = sourceCodeValidationResult;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleSourceCode {
        public Long id;
        public String uid;
        public boolean valid;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SimpleSourceCodesResult extends BaseDataClass {
        public List<SimpleSourceCode> items;

        public SimpleSourceCodesResult(List<SimpleSourceCode> items) {
            this.items = items;
        }

        @JsonCreator
        public SimpleSourceCodesResult (
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodesResult extends BaseDataClass {
        public Slice<SourceCode> items;
        public EnumsApi.DispatcherAssetMode assetMode;
        public List<String> experiments;
        public List<String> batches;

        private boolean isExperiment(String uid) {
            return experiments.contains(uid);
        }

        private boolean isBatch(String uid) {
            return batches.contains(uid);
        }

        @JsonCreator
        public SourceCodesResult (
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public EnumsApi.SourceCodeType getType(String uid) {
            if (isBatch(uid)) {
                return EnumsApi.SourceCodeType.batch;
            }
            else if (isExperiment(uid)) {
                return EnumsApi.SourceCodeType.experiment;
            }
            return EnumsApi.SourceCodeType.common;
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeResult extends BaseDataClass {
        public Long id;
        public Integer version;
        public String uid;
        public Long companyId;
        public long createdOn;
        public boolean locked;
        public boolean valid;
        public String source;
        public EnumsApi.DispatcherAssetMode assetMode;

        public EnumsApi.SourceCodeLang lang;
        public SourceCodeValidationResult validationResult = new SourceCodeValidationResult(
                EnumsApi.SourceCodeValidateStatus.NOT_VERIFIED_YET, "Not verified yet");

        public SourceCodeResult(String errorMessage, SourceCodeValidationResult validationResult) {
            this.validationResult = validationResult;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SourceCodeResult(List<String> errorMessage, SourceCodeValidationResult validationResult) {
            this.validationResult = validationResult;
            this.errorMessages = errorMessage;
        }

        public SourceCodeResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        @JsonCreator
        public SourceCodeResult (
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public SourceCodeResult(SourceCode sc, SourceCodeStoredParamsYaml sourceCode, EnumsApi.DispatcherAssetMode assetMode) {
            this.id = sc.getId();
            this.version = sc.getVersion();
            this.uid = sc.getUid();
            this.companyId = sc.getCompanyId();
            this.createdOn = sc.getCreatedOn();
            this.locked = sc.isLocked();
            this.valid = sc.isValid();

            this.source = sourceCode.source;
            this.lang = sourceCode.lang;
            this.assetMode = assetMode;
        }

        public SourceCodeResult(SourceCode sc, EnumsApi.SourceCodeLang lang, String sourceCode, EnumsApi.DispatcherAssetMode assetMode) {
            this.id = sc.getId();
            this.version = sc.getVersion();
            this.uid = sc.getUid();
            this.companyId = sc.getCompanyId();
            this.createdOn = sc.getCreatedOn();
            this.locked = sc.isLocked();
            this.valid = sc.isValid();

            this.source= sourceCode;
            this.lang = lang;
            this.assetMode = assetMode;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExecContextResult extends BaseDataClass {
        public ExecContext execContext;
        public SourceCode sourceCode;

        @JsonCreator
        public ExecContextResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

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

        public ExecContextResult(SourceCode sourceCode, ExecContext execContext, @Nullable List<String> infoMessages, @Nullable List<String> errorMessages) {
            this.sourceCode = sourceCode;
            this.execContext = execContext;
            if (!CollectionUtils.isEmpty(infoMessages)) {
                this.addInfoMessages(infoMessages);
            }
            if (!CollectionUtils.isEmpty(errorMessages)) {
                this.addErrorMessages(errorMessages);
            }
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

        @JsonCreator
        public ExecContextForDeletion (
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

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
        @Nullable
        public String error;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceCodeValidation extends BaseDataClass {
        public SourceCodeValidationResult status;

        @JsonCreator
        public SourceCodeValidation(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SourceCodeListResult extends BaseDataClass {
        public SourceCode sourceCode;
        public long currentSourceCodeId;

        @JsonCreator
        public SourceCodeListResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    // !!! DO NOT CHANGE THIS CLASS UNDER ANY CIRCUMSTANCES
    @Data
    public static class SourceCodeInternalParamsYaml {
        public boolean archived;
    }

}
