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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test pinning RequestDispatcherForNewTaskEvent equality semantics.
 *
 * Dedup history: the processor-side MultiTenantedQueue (checkForDouble=true)
 * uses Object#equals() via LinkedList#contains() to coalesce duplicates inside
 * a per-tenant queue. When this class was a record with both `params` and
 * `messageId` as components, the auto-generated equals() discriminated on
 * messageId, so dispatcher publishes 101, 102, 103, ... all remained distinct
 * and the tenant queue accumulated dozens of entries draining at the MTQ's
 * postProcessingDelay (1 second) cadence. See ws-stuck-task investigation
 * 2026-04-11 and related log "777.060 event task:N, msgId: N+1, queue size: M".
 *
 * The logical dedup key is WebsocketEventType only.
 *
 * @author Sergio Lissner
 */
public class RequestDispatcherForNewTaskEventEqualsTest {

    private static RequestDispatcherForNewTaskEvent taskEvent(long messageId) {
        WebsocketEventParams p = new WebsocketEventParams();
        p.type = Enums.WebsocketEventType.task;
        p.eventId = messageId;
        return new RequestDispatcherForNewTaskEvent(p, messageId);
    }

    /**
     * Core dedup invariant: two "task" events with different messageIds must
     * be .equals() so MultiTenantedQueue.checkForDouble coalesces them.
     */
    @Test
    public void sameType_differentMessageId_shouldDedup() {
        RequestDispatcherForNewTaskEvent a = taskEvent(101L);
        RequestDispatcherForNewTaskEvent b = taskEvent(102L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    /**
     * Different tenant keys must not collapse: a function-type event must not
     * be coalesced away by a queued task-type event (or vice versa).
     */
    @Test
    public void differentType_shouldNotDedup() {
        RequestDispatcherForNewTaskEvent aTask = taskEvent(101L);

        WebsocketEventParams fp = new WebsocketEventParams();
        fp.type = Enums.WebsocketEventType.function;
        fp.eventId = 101L;
        RequestDispatcherForNewTaskEvent aFunction = new RequestDispatcherForNewTaskEvent(fp, 101L);

        assertThat(aTask).isNotEqualTo(aFunction);
    }

    /**
     * Identical events remain dedup-compatible. (Trivially true, but it
     * protects against a future regression where equals is overridden in a
     * way that breaks reflexivity or symmetry.)
     */
    @Test
    public void sameType_sameMessageId_shouldDedup() {
        RequestDispatcherForNewTaskEvent a = taskEvent(101L);
        RequestDispatcherForNewTaskEvent b = taskEvent(101L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
