/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.api.data.event;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:36 PM
 */
@Data
public class DispatcherEventYaml implements BaseParams {
    public final int version = 1;

    // representation of LocalDateTime
    public String createdOn;
    public EnumsApi.DispatcherEventType event;
    public @Nullable String contextId;

    public BatchEventData batchData;
    public TaskEventData taskData;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class BatchEventData {
        public @Nullable Long size;
        public @Nullable String filename;
        public String username;
        public @Nullable Long batchId;
        public @Nullable Long execContextId;

        // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
        public Long companyId;
    }

    @Data
    public static class TaskEventData {
        // actually this is a coreId but won't be changed because of compatibility with 3rd party apps reason
        @Nullable
        public Long processorId;
        public Long taskId;
        public Long execContextId;
    }
}
