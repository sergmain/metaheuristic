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

package ai.metaheuristic.ai.processor.event;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParams;
import ai.metaheuristic.commons.utils.threads.EventWithId;

/**
 * @author Sergio Lissner
 * Date: 2/21/2024
 * Time: 2:03 PM
 */
public class RequestDispatcherForNewTaskEvent implements EventWithId<Enums.WebsocketEventType> {

    public final WebsocketEventParams params;
    public final long messageId;

    public RequestDispatcherForNewTaskEvent(WebsocketEventParams params, long messageId) {
        this.params = params;
        this.messageId = messageId;
    }

    @Override
    public Enums.WebsocketEventType getId() {
        return params.type;
    }

    // Dedup key is the logical event type, NOT messageId. MultiTenantedQueue
    // with checkForDouble=true uses .equals() to coalesce duplicates inside a
    // per-tenant queue. The dispatcher may publish many distinct messageIds
    // (101, 102, 103, ...) for the same logical "there is a task" event; we
    // want all but one of them dropped while a task is already queued for
    // this WebsocketEventType.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestDispatcherForNewTaskEvent that)) return false;
        return this.params.type == that.params.type;
    }

    @Override
    public int hashCode() {
        return params.type.hashCode();
    }
}
