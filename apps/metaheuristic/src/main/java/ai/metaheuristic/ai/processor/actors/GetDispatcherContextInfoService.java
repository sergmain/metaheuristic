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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.tasks.GetDispatcherContextInfoTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 10:38 AM
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class GetDispatcherContextInfoService extends AbstractTaskQueue<GetDispatcherContextInfoTask> implements QueueProcessor {

    private final Globals globals;

    @Override
    public void process() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        GetDispatcherContextInfoTask task;
        while ((task = poll()) != null) {
        }
    }
}
