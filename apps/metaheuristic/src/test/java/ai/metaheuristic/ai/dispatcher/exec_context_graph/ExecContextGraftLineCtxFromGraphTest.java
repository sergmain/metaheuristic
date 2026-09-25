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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.utils.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExecContextGraftService#attachGroup} takes the taskContextIds it chooses a fresh line ctx from out of the
 * ExecContext's GRAPH ({@link ExecContextGraphService#findAllTaskContextIds}) instead of loading every Task. That is
 * sound only because a Task exists only as a vertex of its ExecContext's graph - so the graph's taskContextIds and the
 * Tasks' own are one set. This test checks exactly that on a real ExecContext, before and after each of three grafts,
 * with the Tasks' own taskContextIds (read from the Task records) as the independent side of the comparison; and that
 * the lines the grafts take are fresh and consecutive siblings.
 *
 * <p>Also per VERTEX, not only as a set: {@code ExecContextGraftTxService.createGroupTasksTx} filters the target's
 * children for line isolation by each child vertex's own taskContextId, so every vertex must carry exactly its Task's
 * taskContextId - checked as the map taskId -> taskContextId, graph against Task records.
 *
 * <p>V3 harness: extends PreparingSourceCode -> PreparingCore -> MhSharedItTest (shared Spring context + H2 DB; no
 * @DirtiesContext; cleanup inherited). Set up exactly as {@link ExecContextGraftConcurrentAttachTest}.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@AutoConfigureCache
public class ExecContextGraftLineCtxFromGraphTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private InternalFunctionService internalFunctionService;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextGraphService execContextGraphService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_graftsReadTheContextsTheTasksCarry() {
        // 1. create EC + produce real tasks WITHOUT starting (STOPPED - the graft target state).
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long ecId = getExecContextForTest().id;

        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, () ->
                                txSupportForTestingService.produceTasksWithoutStarting(getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        final Long graphId = getExecContextForTest().execContextGraphId;

        // 2. find a target task whose getSubProcesses resolves a non-empty SEQUENTIAL body.
        final ExecContextImpl ec = getExecContextForTest();
        final ExecContextApiData.SimpleExecContext sec = ec.asSimple();
        Long targetTaskId = null;
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            InternalFunctionData.ExecutionContextData ecd =
                    internalFunctionService.getSubProcesses(sec, t.getTaskParamsYaml(), t.id);
            if (ecd.internalFunctionProcessingResult.processing == Enums.InternalFunctionProcessing.ok
                    && !ecd.subProcesses.isEmpty()
                    && ecd.process.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                targetTaskId = t.id;
                break;
            }
        }
        assertNotNull(targetTaskId, "FIXTURE: no task with a graftable sub-process body found in the produced EC");

        assertEquals(taskRecordContextIds(ecId), execContextGraphService.findAllTaskContextIds(graphId),
                "PHASE #1: before any graft, the graph must carry exactly the taskContextIds the Tasks carry");
        assertEquals(taskRecordContextsById(ecId), graphVertexContextsById(graphId),
                "PHASE #1: before any graft, every vertex must carry exactly its Task's taskContextId");

        // 3. three grafts in a row under the same target
        final List<String> lineCtxIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final Set<String> before = taskRecordContextIds(ecId);
            final ExecContextGraftService.GraftResult graft = execContextGraftService.attachGroup(
                    ecId, targetTaskId,
                    ExecContextGraftService.GroupRef.fromTargetSubProcesses(),
                    List.of(), List.of(),
                    ExecContextGraftService.Driver.PLACE_NOW);
            final String lineCtxId = graft.lineCtxId();
            final int n = i;
            assertFalse(before.contains(lineCtxId),
                    () -> "PHASE #2: graft " + n + " must take a ctx no Task had, observed " + lineCtxId);
            assertEquals(taskRecordContextIds(ecId), execContextGraphService.findAllTaskContextIds(graphId),
                    "PHASE #2: after graft " + n + ", the graph must carry exactly the taskContextIds the Tasks carry");
            assertEquals(taskRecordContextsById(ecId), graphVertexContextsById(graphId),
                    "PHASE #2: after graft " + n + ", every vertex must carry exactly its Task's taskContextId");
            lineCtxIds.add(lineCtxId);
        }

        final String level = ContextUtils.getLevel(lineCtxIds.getFirst());
        assertEquals(List.of(level, level, level), lineCtxIds.stream().map(ContextUtils::getLevel).toList(),
                "PHASE #3: the three lines must be siblings, observed " + lineCtxIds);
        final int first = Integer.parseInt(Objects.requireNonNull(ContextUtils.getPath(lineCtxIds.getFirst())));
        assertEquals(List.of(first, first + 1, first + 2),
                lineCtxIds.stream().map(c -> Integer.parseInt(Objects.requireNonNull(ContextUtils.getPath(c)))).toList(),
                "PHASE #3: each graft must take the next sibling after the previous one, observed " + lineCtxIds);
    }

    /** The Tasks' own taskContextIds, read from the Task records - the independent side of the comparison. */
    private Set<String> taskRecordContextIds(Long ecId) {
        final Set<String> ctxIds = new HashSet<>();
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            final String ctxId = t.getTaskParamsYaml().task.taskContextId;
            if (ctxId != null) {
                ctxIds.add(ctxId);
            }
        }
        return ctxIds;
    }

    /** taskId -> taskContextId as the Task records carry it. */
    private Map<Long, String> taskRecordContextsById(Long ecId) {
        final Map<Long, String> byId = new HashMap<>();
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            byId.put(t.id, t.getTaskParamsYaml().task.taskContextId);
        }
        return byId;
    }

    /** taskId -> taskContextId as the ExecContext's graph carries it, vertex by vertex. */
    private Map<Long, String> graphVertexContextsById(Long graphId) {
        final Map<Long, String> byId = new HashMap<>();
        for (ExecContextData.TaskVertex v : execContextGraphService.findAll(graphId)) {
            byId.put(v.taskId, v.taskContextId);
        }
        return byId;
    }
}
