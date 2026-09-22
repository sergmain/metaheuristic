/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
import org.jspecify.annotations.Nullable;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:36 PM
 */
@Data
public class DispatcherEventYamlV3 implements BaseParams {
    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 3;

    // representation of LocalDateTime
    public String createdOn;
    // type of event. MH's own types are the names of EnumsApi.DispatcherEventType,
    // any other value is a type which is defined outside of MH
    public String event;
    @Nullable
    public String contextId;

    // payload of a generic event, its format is defined by the creator of an event of such type
    @Nullable
    public String params;

    public @Nullable BatchEventDataV3 batchData;
    public @Nullable TaskEventDataV3 taskData;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class BatchEventDataV3 {
        @Nullable
        public Long size;
        @Nullable
        public String filename;
        public String username;
        @Nullable
        public Long batchId;
        @Nullable
        public Long execContextId;

        // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
        public Long companyId;
    }

    @Data
    public static class TaskEventDataV3 {
        @Nullable
        public Long coreId;
        public Long taskId;
        public Long execContextId;
        public EnumsApi.@Nullable FunctionExecContext context;
        @Nullable
        public String funcCode;
    }
}
