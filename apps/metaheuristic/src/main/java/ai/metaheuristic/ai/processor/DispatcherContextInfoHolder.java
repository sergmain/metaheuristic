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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.data.DispatcherData;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 6:16 AM
 */
public class DispatcherContextInfoHolder {

    public static final Map<String, DispatcherData.DispatcherContextInfo> contexts = new HashMap<>();

    @Nullable
    public static DispatcherData.DispatcherContextInfo getCtx(ProcessorAndCoreData.CommonUrl commonUrl) {
        return contexts.get(commonUrl.getUrl());
    }

    public static void put(ProcessorAndCoreData.CommonUrl commonUrl, DispatcherData.DispatcherContextInfo context) {
        contexts.computeIfAbsent(commonUrl.getUrl(), o->new DispatcherData.DispatcherContextInfo()).update(context);
    }

    public static void put(ProcessorAndCoreData.CommonUrl commonUrl, Long chunkSize) {
        contexts.computeIfAbsent(commonUrl.getUrl(), o-> new DispatcherData.DispatcherContextInfo()).chunkSize = chunkSize;
    }
}
