/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sergio Lissner
 * Plan 01 — Foundation. Steps 1, 3.
 * Characterization-style Red for a new feature: test compiles/runs only after
 * the minimal Plan 01 surface (SignalBus, SignalKind, ScopeRef, SignalEntry,
 * SignalKindRegistry, TopicBuilder, CoalescePolicy, QueryResult) exists.
 */
class SignalBusTest {

    private static SignalBus newBatchBus() {
        TopicBuilder batchTopic = (k, id, info) -> "batch." + id + ".state";
        SignalKindRegistry registry = new SignalKindRegistry(
            Map.of(SignalKind.BATCH, batchTopic),
            Map.of(SignalKind.BATCH, CoalescePolicy.NONE));
        return new SignalBus(registry);
    }

    private static SignalBus newBatchAndExecContextBus() {
        TopicBuilder batchTopic = (k, id, info) -> "batch." + id + ".state";
        TopicBuilder ecTopic = (k, id, info) ->
            "execContext." + info.get("infoBank")
            + "." + stripVersionForTest((String) info.get("sourceCodeUid"))
            + ".state";
        SignalKindRegistry registry = new SignalKindRegistry(
            Map.of(SignalKind.BATCH, batchTopic,
                   SignalKind.EXEC_CONTEXT, ecTopic),
            Map.of(SignalKind.BATCH, CoalescePolicy.NONE,
                   SignalKind.EXEC_CONTEXT, CoalescePolicy.NONE));
        return new SignalBus(registry);
    }

    private static String stripVersionForTest(String uid) {
        // tiny inline strip until Step 11 lands TopicUtils.stripVersion
        return uid.replaceFirst("-\\d+\\.\\d+\\.\\d+$", "");
    }

    private static SignalBus newDocumentExportBus(java.time.Clock clock) {
        TopicBuilder exportTopic = (k, id, info) ->
            "document.export." + info.get("projectId") + ".progress";
        SignalKindRegistry registry = new SignalKindRegistry(
            Map.of(SignalKind.DOCUMENT_EXPORT, exportTopic),
            Map.of(SignalKind.DOCUMENT_EXPORT,
                new CoalescePolicy(java.time.Duration.ofMillis(100))));
        return new SignalBus(registry, clock);
    }

    @Test
    void put_thenQueryReturnsEntry() {
        // arrange
        SignalBus bus = newBatchBus();
        ScopeRef scope = new ScopeRef(100L);

        // act
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 4), true);
        QueryResult result = bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());

        // assert
        assertThat(result.signals()).hasSize(1);
        SignalEntry e = result.signals().get(0);
        assertThat(e.signalId()).isEqualTo("42");
        assertThat(e.revision()).isEqualTo(1L);
        assertThat(e.info()).containsEntry("state", 4);
        assertThat(e.terminal()).isTrue();
        assertThat(e.topic()).isEqualTo("batch.42.state");
        assertThat(result.serverRev()).isEqualTo(1L);
    }

    @Test
    void put_overwritesSameSignalId() {
        // arrange
        SignalBus bus = newBatchBus();
        ScopeRef scope = new ScopeRef(100L);

        // act: two publishes for the same (kind, signalId)
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 2), false);
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 4), true);
        var result = bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());

        // assert
        assertThat(result.signals()).hasSize(1);                              // overwrite, not append
        assertThat(result.signals().get(0).info()).containsEntry("state", 4);
        assertThat(result.signals().get(0).revision()).isEqualTo(2L);          // bumped
        assertThat(result.signals().get(0).terminal()).isTrue();
        assertThat(result.serverRev()).isEqualTo(2L);
    }

    @Test
    void query_afterRev_returnsOnlyNewer() {
        // arrange
        SignalBus bus = newBatchBus();
        ScopeRef scope = new ScopeRef(100L);

        // act
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 2), false);   // rev 1
        long w1 = bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of()).serverRev();
        bus.put(SignalKind.BATCH, "43", scope, Map.of("state", 3), false);   // rev 2
        var result = bus.query(scope, w1, Set.of(SignalKind.BATCH), List.of());

        // assert
        assertThat(result.signals()).hasSize(1);
        assertThat(result.signals().get(0).signalId()).isEqualTo("43");
        assertThat(result.signals().get(0).revision()).isEqualTo(2L);
        assertThat(result.serverRev()).isEqualTo(2L);
    }

    @Test
    void query_afterRev_idlePoll_returnsEmptyButSameServerRev() {
        // arrange: covers the watermark contract — serverRev does NOT advance during idle polls
        SignalBus bus = newBatchBus();
        ScopeRef scope = new ScopeRef(100L);
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 2), false);   // rev 1
        long w1 = bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of()).serverRev();

        // act: no new publishes between the two queries
        var result = bus.query(scope, w1, Set.of(SignalKind.BATCH), List.of());

        // assert
        assertThat(result.signals()).isEmpty();
        assertThat(result.serverRev()).isEqualTo(w1);
    }

    @Test
    void query_scopeFilter_excludesOtherCompanies() {
        // arrange
        SignalBus bus = newBatchBus();

        // act
        bus.put(SignalKind.BATCH, "42", new ScopeRef(100L), Map.of("state", 2), false);
        bus.put(SignalKind.BATCH, "43", new ScopeRef(200L), Map.of("state", 3), false);
        var result = bus.query(new ScopeRef(100L), 0L, Set.of(SignalKind.BATCH), List.of());

        // assert
        assertThat(result.signals())
            .extracting(SignalEntry::signalId)
            .containsExactly("42");
    }

    @Test
    void query_topicGlob_filtersByPattern() {
        // arrange
        SignalBus bus = newBatchAndExecContextBus();
        ScopeRef scope = new ScopeRef(100L);

        bus.put(SignalKind.EXEC_CONTEXT, "100", scope,
            Map.of("infoBank", "DRONE", "sourceCodeUid", "mhdg-rg-flat-1.0.0"), false);
        bus.put(SignalKind.EXEC_CONTEXT, "101", scope,
            Map.of("infoBank", "DRONE", "sourceCodeUid", "cv-redundancy-1.0.0"), false);

        // act
        var all = bus.query(scope, 0L, Set.of(SignalKind.EXEC_CONTEXT), List.of());
        var cvOnly = bus.query(scope, 0L, Set.of(SignalKind.EXEC_CONTEXT),
            List.of(new GlobPattern("execContext.DRONE.cv-*.state")));

        // assert
        assertThat(all.signals()).hasSize(2);
        assertThat(cvOnly.signals()).hasSize(1);
        assertThat(cvOnly.signals().get(0).signalId()).isEqualTo("101");
    }

    @Test
    void query_topicGlob_appliedAfterScope_excludesOtherCompanyMatchingGlob() {
        // arrange: a different company's signal would match the glob,
        // but is excluded by scope.
        SignalBus bus = newBatchAndExecContextBus();
        ScopeRef mine = new ScopeRef(100L);
        ScopeRef other = new ScopeRef(200L);

        bus.put(SignalKind.EXEC_CONTEXT, "100", mine,
            Map.of("infoBank", "DRONE", "sourceCodeUid", "cv-redundancy-1.0.0"), false);
        bus.put(SignalKind.EXEC_CONTEXT, "200", other,
            Map.of("infoBank", "DRONE", "sourceCodeUid", "cv-redundancy-1.0.0"), false);

        // act
        var result = bus.query(mine, 0L, Set.of(SignalKind.EXEC_CONTEXT),
            List.of(new GlobPattern("execContext.DRONE.cv-*.state")));

        // assert
        assertThat(result.signals())
            .extracting(SignalEntry::signalId)
            .containsExactly("100");
    }

    @Test
    void put_coalesceWindow_suppressesInterimRevisions() {
        // arrange: DOCUMENT_EXPORT registered with minInterval = 100ms
        MutableClock clock = new MutableClock(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        SignalBus bus = newDocumentExportBus(clock);
        ScopeRef scope = new ScopeRef(100L);

        // act: first publish, then a second within the coalesce window
        bus.put(SignalKind.DOCUMENT_EXPORT, "export:42:xyz", scope,
            Map.of("projectId", 42L, "percent", 10.0), false);
        long r1 = bus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of()).serverRev();

        clock.advance(java.time.Duration.ofMillis(50));   // still inside 100ms window

        bus.put(SignalKind.DOCUMENT_EXPORT, "export:42:xyz", scope,
            Map.of("projectId", 42L, "percent", 20.0), false);
        long r2 = bus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of()).serverRev();

        // assert: revision did NOT advance, but info was updated
        assertThat(r2).isEqualTo(r1);
        var entry = bus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of())
            .signals().get(0);
        assertThat(entry.info()).containsEntry("percent", 20.0);
    }

    @Test
    void put_terminal_bypassesCoalesce() {
        // arrange
        MutableClock clock = new MutableClock(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        SignalBus bus = newDocumentExportBus(clock);
        ScopeRef scope = new ScopeRef(100L);

        bus.put(SignalKind.DOCUMENT_EXPORT, "export:42:xyz", scope,
            Map.of("projectId", 42L, "percent", 90.0), false);
        long r1 = bus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of()).serverRev();

        // act: well within the 100ms coalesce window, but terminal=true must push through
        clock.advance(java.time.Duration.ofMillis(20));
        bus.put(SignalKind.DOCUMENT_EXPORT, "export:42:xyz", scope,
            Map.of("projectId", 42L, "percent", 100.0, "phase", "finished"), true);

        // assert
        long r2 = bus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of()).serverRev();
        assertThat(r2).isGreaterThan(r1);
        var entry = bus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of())
            .signals().get(0);
        assertThat(entry.terminal()).isTrue();
        assertThat(entry.info()).containsEntry("phase", "finished");
    }

    @Test
    void concurrent_publish_and_query_noLostWrites() throws Exception {
        // arrange: bounded property check that the RW lock actually protects invariants.
        // A missing writeLock().unlock() in a finally block would make this flaky.
        SignalBus bus = newBatchBus();
        ScopeRef scope = new ScopeRef(100L);
        final int producerCount = 4;
        final int perProducer  = 250;
        final int readerCount  = 2;
        final int totalEntries = producerCount * perProducer;

        java.util.concurrent.ExecutorService exec =
            java.util.concurrent.Executors.newFixedThreadPool(producerCount + readerCount);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch producersDone = new java.util.concurrent.CountDownLatch(producerCount);
        java.util.concurrent.atomic.AtomicBoolean stopReaders = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicLong readerErrors = new java.util.concurrent.atomic.AtomicLong(0);

        // producers — each owns a disjoint id range so signalIds across all threads are unique
        for (int p = 0; p < producerCount; p++) {
            final int producerIndex = p;
            exec.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perProducer; i++) {
                        String id = "p" + producerIndex + "-i" + i;
                        bus.put(SignalKind.BATCH, id, scope, Map.of("state", 4), true);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
        }

        // readers — keep querying until producers finish
        for (int r = 0; r < readerCount; r++) {
            exec.submit(() -> {
                try {
                    start.await();
                    while (!stopReaders.get()) {
                        bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ex) {
                    readerErrors.incrementAndGet();
                }
            });
        }

        // act
        start.countDown();
        boolean finished = producersDone.await(30, java.util.concurrent.TimeUnit.SECONDS);
        stopReaders.set(true);
        exec.shutdown();
        boolean exited = exec.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // assert
        assertThat(finished).as("producers did not finish in time").isTrue();
        assertThat(exited).as("executor did not terminate cleanly").isTrue();
        assertThat(readerErrors.get()).as("readers saw exceptions").isEqualTo(0);

        var finalResult = bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());
        assertThat(finalResult.signals()).hasSize(totalEntries);
        assertThat(finalResult.serverRev()).isEqualTo(totalEntries);
    }
}
