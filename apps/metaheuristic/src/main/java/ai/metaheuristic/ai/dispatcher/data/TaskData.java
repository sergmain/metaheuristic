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

import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 6:32 PM
 */
public class TaskData {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceTaskResult {
        public EnumsApi.TaskProducingStatus status;
        public @Nullable String error;
        public Long taskId;

        public ProduceTaskResult(EnumsApi.TaskProducingStatus status, @Nullable String error) {
            this.status = status;
            this.error = error;
        }
    }

    @Data
    @AllArgsConstructor
    public static class TaskWithInternalContext {
        public final Long sourceCodeId;
        public final Long execContextId;
        public final Long taskId;
    }
}
