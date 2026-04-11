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

package ai.metaheuristic.commons.utils.threads;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for MultiTenantedQueue#checkForDouble semantics.
 *
 * Scenario: multiple distinct event instances share the same tenant id.
 * Expected (intended) semantics of checkForDouble=true:
 *   while a task is already queued (or running) for a given tenant id,
 *   additional puts for the same tenant id should be ignored -- one queued
 *   head is sufficient to re-trigger the desired work.
 *
 * This reproduces the processor-side symptom where the DispatcherRequestor
 * MTQ (keyed by WebsocketEventType) accumulated dozens of 'task' entries
 * because each RequestDispatcherForNewTaskEvent was a fresh instance and
 * LinkedList.contains() was never matching.
 *
 * @author Claude / Sergio Lissner
 */
public class MultiTenantedQueueCheckForDoubleTest {

    /** Distinct instance per event; default equals (identity). */
    private static class TaskEvent implements EventWithId<String> {
        final int seq;
        TaskEvent(int seq) { this.seq = seq; }
        @Override
        public String getId() { return "task"; }
    }

    @Test
    public void sameTenantId_distinctInstances_dedupedToOne() throws InterruptedException {
        final CountDownLatch releaseProcessor = new CountDownLatch(1);
        final CountDownLatch firstEntered = new CountDownLatch(1);
        final AtomicInteger processed = new AtomicInteger(0);

        final MultiTenantedQueue<String, TaskEvent> q = new MultiTenantedQueue<>(
            10, Duration.ZERO, true, "test-dedup-",
            event -> {
                firstEntered.countDown();
                try {
                    releaseProcessor.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                processed.incrementAndGet();
            });

        try {
            // Put the first event and wait until the processor thread is
            // actually inside processFunc (so the head has been pulled).
            q.putToQueue(new TaskEvent(1));
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // While the processor is blocked, enqueue many more distinct
            // instances for the same tenant id. With correct checkForDouble
            // semantics, all of these should be dropped because one task
            // is already in-flight for tenant "task".
            for (int i = 2; i <= 10; i++) {
                q.putToQueue(new TaskEvent(i));
            }

            // Step 3 - Green (post-fix): the head (seq=1) has been pulled and
            // is in flight, so the per-tenant queue is empty at the moment
            // seq=2 arrives -- it is accepted (coalescing "something new to
            // do after current work finishes"). Every subsequent put (seq=3
            // through seq=10) finds the tenant queue non-empty and is
            // dropped. Final queue size == 1.
            assertThat(q.size("task")).isEqualTo(1);
        } finally {
            releaseProcessor.countDown();
            q.shutdown();
        }
    }
}
