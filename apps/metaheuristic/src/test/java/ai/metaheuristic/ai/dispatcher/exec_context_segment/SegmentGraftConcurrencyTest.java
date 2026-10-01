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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.6): out-of-band PLACE_NOW grafts into one ExecContext run in
 * parallel. 16 threads x 50 grafts under one splitter of a produced S1 ExecContext: every graft succeeds (no optimistic
 * lock failure, no other failure), 800 new segment rows exist, every line ctx is unique and is {@code 1,2#(seed + its
 * segment's id)} with seed 0, and no join record is written.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentGraftConcurrencyTest extends PreparingSourceCode {

    private static final int THREADS = 16;
    private static final int GRAFTS_PER_THREAD = 50;

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    @Test
    public void test_16threads_x_50_placeNow_intoOneExecContext() throws InterruptedException {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        setExecContextForTest(ec);
        final Long ecId = ec.id;
        final Long splitterId = support.rows(ecId).stream().filter(r -> "splitter".equals(r.processCode())).findFirst().orElseThrow().id();
        final Set<Long> segmentsBefore = new HashSet<>(segmentRepository.findIdsByExecContextId(ecId));

        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        final List<Future<List<ExecContextGraftService.GraftResult>>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    final List<ExecContextGraftService.GraftResult> results = new ArrayList<>();
                    for (int i = 0; i < GRAFTS_PER_THREAD; i++) {
                        results.add(execContextGraftService.attachGroup(ecId, splitterId, new ExecContextGraftService.GroupRef("line"),
                                List.of(), List.of(), ExecContextGraftService.Driver.PLACE_NOW, "mh.nop"));
                    }
                    return results;
                }));
            }
            await().atMost(Duration.ofMinutes(10)).pollInterval(Duration.ofSeconds(1))
                    .until(() -> futures.stream().allMatch(Future::isDone));
        }
        finally {
            pool.shutdownNow();
        }

        final List<ExecContextGraftService.GraftResult> results = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        for (Future<List<ExecContextGraftService.GraftResult>> f : futures) {
            try {
                results.addAll(f.get());
            }
            catch (ExecutionException e) {
                failures.add(e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
        }
        assertEquals(List.of(), failures, "every graft thread completes without a failure (optimistic lock or other)");
        assertEquals(THREADS * GRAFTS_PER_THREAD, results.size());

        final Set<String> ctxs = new HashSet<>();
        results.forEach(r -> assertTrue(ctxs.add(r.lineCtxId()), "line ctx " + r.lineCtxId() + " was allocated twice"));

        final List<ExecContextSegment> added = segmentRepository.findIdsByExecContextId(ecId).stream()
                .filter(id -> !segmentsBefore.contains(id))
                .map(id -> segmentRepository.findById(id).orElseThrow())
                .toList();
        assertEquals(THREADS * GRAFTS_PER_THREAD, added.size(), "one new segment row per graft");
        for (ExecContextSegment s : added) {
            assertEquals("1,2#" + s.id, s.lineCtxId, "decision 10: base#(seed 0 + the segment's id)");
            assertEquals(splitterId, s.forkTaskId);
        }
        assertEquals(ctxs, new HashSet<>(added.stream().map(s -> s.lineCtxId).toList()), "the returned line ctxs are the new segments");
        assertEquals(List.of(), joinRepository.findIdsByExecContextId(ecId), "PLACE_NOW writes no join record");

        invariants.assertAll(ecId);
    }
}
