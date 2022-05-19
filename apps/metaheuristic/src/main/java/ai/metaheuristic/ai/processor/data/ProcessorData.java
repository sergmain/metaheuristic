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

package ai.metaheuristic.ai.processor.data;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 1/4/2021
 * Time: 10:31 AM
 */
public class ProcessorData {

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of={"processorId", "coreCode", "dispatcherUrl"})
    public static class ProcessorCoreAndProcessorIdAndDispatcherUrlRef {
        public final ProcessorAndCoreData.DispatcherUrl dispatcherUrl;
        public final String dispatcherCode;
        public final Long processorId;
        public final String coreCode;
        public final Long coreId;
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of={"processorId", "dispatcherUrl"})
    public static class ProcessorCodeAndIdAndDispatcherUrlRef {
        public final ProcessorAndCoreData.DispatcherUrl dispatcherUrl;
        public final String dispatcherCode;
        public final Long processorId;
    }
}
