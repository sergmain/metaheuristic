/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.event.events;

import ai.metaheuristic.ai.dispatcher.event.events.UnAssignTaskEvent;
import lombok.AllArgsConstructor;

/**
 * @author Serge
 * Date: 12/1/2021
 * Time: 10:43 PM
 */
@AllArgsConstructor
public class UnAssignTaskTxAfterCommitEvent {
    public final Long execContextId;
    public final Long taskId;

    public UnAssignTaskEvent to() {
        return new UnAssignTaskEvent(execContextId, taskId);
    }
}
