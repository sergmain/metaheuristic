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

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 6:16 AM
 */
@Service
@Slf4j
@Profile("processor")
public class DispatcherContextService {

    public static final Map<ProcessorAndCoreData.CommonUrl, DispatcherContextInfo> contexts = new HashMap<>();

    public DispatcherContextInfo getCtx(ProcessorAndCoreData.CommonUrl commonUrl) {
        return contexts.get(commonUrl);
    }

    public void put(ProcessorAndCoreData.CommonUrl commonUrl, DispatcherContextInfo context) {
        // ###IDEA###, why?
        contexts.putIfAbsent(commonUrl, context).update(context);
    }
}
