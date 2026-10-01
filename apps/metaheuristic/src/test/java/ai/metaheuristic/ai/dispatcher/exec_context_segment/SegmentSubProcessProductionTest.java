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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 041 Phase 22 - initial production of a process whose sub-processes are produced at the same time: an EXTERNAL
 * function carrying a {@code logic: and} block ({@code dataset-processing} of
 * {@code /source_code/yaml/default-source-code-for-testing.yaml}). Its sub-process Tasks are lines forked from it,
 * and the process after it is produced with the process graph's parents - itself plus the tails of those lines. In
 * segments the tail -> join edges are derived, never stored.
 *
 * <p>V3 harness: extends PreparingSourceCode; no @DirtiesContext, no per-class @DynamicPropertySource.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentSubProcessProductionTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextSegmentReadService segmentReadService;
    @Autowired private ExecContextJoinRepository joinRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private SegmentInvariantAsserts invariants;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_externalProcessWithAndSubProcesses_nextProcessFollowsTheForkAndJoinsItsLines() {
        final DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        final ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long ecId = getExecContextForTest().id;

        // Green-1 pinned: the writer refused the process after dataset-processing (01.913.020) - its parents are the
        // fork plus the tails of the two sub-process lines, and only the line's tail was accepted.
        // Desired: production succeeds, and the edges derived from the segments are the process graph's edges.
        produce(ecId);

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);
        final SegmentData.Graph graph = segmentReadService.graph(ecId);
        final Map<Long, Set<Long>> derivedParents = new TreeMap<>();
        graph.edges().forEach(edge -> derivedParents.computeIfAbsent(edge.to(), k -> new TreeSet<>()).add(edge.from()));

        assertEquals(tasks.size(), graph.nodes().size(), "every produced Task is in the segments, ExecContext #" + ecId);
        final Map<String, TaskImpl> byCode = new HashMap<>();
        for (TaskImpl t : tasks) {
            final var task = t.getTaskParamsYaml().task;
            byCode.put(task.processCode, t);
            assertEquals(new TreeSet<>(task.init.parentTaskIds), derivedParents.getOrDefault(t.id, new TreeSet<>()),
                    "parents of Task #" + t.id + " " + task.processCode + "@" + task.taskContextId
                    + ": the process graph's (init.parentTaskIds) vs derived from the segments");
        }

        final TaskImpl fork = Objects.requireNonNull(byCode.get("dataset-processing"));
        final TaskImpl join = Objects.requireNonNull(byCode.get("mh.permute-values-of-variables"));
        assertEquals(3, join.getTaskParamsYaml().task.init.parentTaskIds.size(),
                "the process after dataset-processing has the fork and the two sub-process tails as parents");
        assertTrue(join.getTaskParamsYaml().task.init.parentTaskIds.contains(fork.id));

        final ExecContextJoin record = joinRepository.findByExecContextIdAndJoinTaskId(ecId, join.id);
        assertNotNull(record, "the two sub-process lines are registered with their join, Task #" + join.id);
        assertEquals(2, record.linesRegistered, "lines registered with join #" + join.id);
        assertEquals(0, record.linesFinished);
        assertEquals(0, record.linesDead);

        invariants.assertAll(ecId);
    }

    private void produce(Long ecId) {
        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(ecId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(ecId, () ->
                                txSupportForTestingService.produceTasksWithoutStarting(getSourceCode(), ecId))));
    }
}
