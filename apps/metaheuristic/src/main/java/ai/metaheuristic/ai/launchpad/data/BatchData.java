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

package ai.metaheuristic.ai.launchpad.data;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.batch.data.BatchExecStatus;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.launchpad.Plan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:21 PM
 */
public final class BatchData {


    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    public static class ExecStatuses extends BaseDataClass {
        public List<BatchExecStatus> statuses;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class PlansForBatchResult extends BaseDataClass {
        public List<Plan> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessResourceItem {
        public Batch batch;
        public String planCode;
        public String execStateStr;
        public int execState;
        public boolean ok;
        public String uploadedFileName;
        public String username;
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
        public Page<ProcessResourceItem> batches;
        public EnumsApi.LaunchpadAssetMode assetMode;
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
