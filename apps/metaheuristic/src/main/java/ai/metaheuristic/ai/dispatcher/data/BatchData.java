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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchExecStatus;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:21 PM
 */
public final class BatchData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkOperation {
        public Long batchId;
        public OperationStatusRest status;
    }

    @Data
    public static class BulkOperations {
        public List<BulkOperation> operations = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UploadingStatus extends BaseDataClass {
        public Long batchId;
        public Long execContextId;

        public UploadingStatus(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public UploadingStatus(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    public static class ExecStatuses extends BaseDataClass {
        public List<BatchExecStatus> statuses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchExecInfo {
        public Batch batch;
        public String sourceCodeUid;
        public String execStateStr;
        public int execState;
        public boolean ok;
        public String uploadedFileName;
        public String username;
        public boolean execContextDeleted;

        public boolean finished() {
            return execState==Enums.BatchExecState.Finished.code ||  execState==Enums.BatchExecState.Error.code || execState==Enums.BatchExecState.Archived.code;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Status extends BaseDataClass {
        public Long batchId;
        public String console;
        public boolean ok;

        public Status(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public Status(Long batchId, String console, boolean ok) {
            this.batchId = batchId;
            this.console = console;
            this.ok = ok;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class BatchesResult extends BaseDataClass {
        public Page<BatchExecInfo> batches;
        public EnumsApi.DispatcherAssetMode assetMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class BatchResult extends BaseDataClass {
        public Batch batch;

        public BatchResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public BatchResult(Batch batch) {
            this.batch = batch;
        }
    }

}
