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

package ai.metaheuristic.ai.processor;

import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 6:16 AM
 */
public class DispatcherContextInfoHolder {

    public static final Map<String, DispatcherContextInfo> contexts = new HashMap<>();

    @Nullable
    public static DispatcherContextInfo getCtx(ProcessorAndCoreData.CommonUrl commonUrl) {
        return contexts.get(commonUrl.getUrl());
    }

    public static void put(ProcessorAndCoreData.CommonUrl commonUrl, DispatcherContextInfo context) {
        contexts.computeIfAbsent(commonUrl.getUrl(), o->new DispatcherContextInfo()).update(context);
    }

    public static void put(ProcessorAndCoreData.CommonUrl commonUrl, Long chunkSize) {
        contexts.computeIfAbsent(commonUrl.getUrl(), o-> new DispatcherContextInfo()).chunkSize = chunkSize;
    }
}
