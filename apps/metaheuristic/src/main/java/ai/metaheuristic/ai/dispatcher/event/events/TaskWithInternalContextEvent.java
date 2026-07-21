/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.commons.utils.threads.EventWithId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:55 PM
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(of={"taskId"})
public class TaskWithInternalContextEvent implements EventWithId<Long> {
    public final Long sourceCodeId;
    public final Long execContextId;
    public final Long taskId;

    // tenant key for MultiTenantedQueue: taskId => one virtual thread per ready internal-function Task,
    // enabling parallel execution of internal Functions (including within a single ExecContext).
    // The DAG contract is preserved upstream of the queue: findAllForAssigning only emits an event
    // for a Task whose upstream vertices are already complete.
    @Override
    public Long getId() {
        return taskId;
    }
}
