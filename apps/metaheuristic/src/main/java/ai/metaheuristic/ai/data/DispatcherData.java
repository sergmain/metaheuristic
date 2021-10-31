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

package ai.metaheuristic.ai.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 10:26 PM
 */
public class DispatcherData {

    /**
     * @author Serge
     * Date: 5/29/2019
     * Time: 12:45 AM
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherContextInfo {

        // chunkSize must be inited with value from Dispatcher. Until then Processor will wait for initializing
        public Long chunkSize;

        public Integer maxVersionOfProcessor;

        public void update(DispatcherContextInfo context) {
            this.chunkSize = context.chunkSize;
            this.maxVersionOfProcessor = context.maxVersionOfProcessor;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class AllocatedQuotas {
        public final Long taskId;
        public final String tag;
        public final int amount;
    }

    @Data
    @RequiredArgsConstructor
    public static class TaskQuotas {
        public final int initial;

        public final List<AllocatedQuotas> allocated = new ArrayList<>();
    }
}
