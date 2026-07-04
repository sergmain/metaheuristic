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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DSL v2 graft primitive - the @Transactional write core of {@link ExecContextGraftService}
 * (025-MHSC-DSL-V2-PLAN, Phase 1). Pure MH: no snapshot / requirement / objection awareness.
 *
 * <p>Three writes, each its own transaction, each invoked by the orchestrator while holding the
 * relevant sync write locks (SPRING-TX-RULES sec 1/sec 2):
 * <ol>
 *   <li>{@link #createGroupTasksTx} - write bound inputs at the fresh line ctx, instantiate the
 *       body sub-graph PRE_INIT rooted under the target, and wire the tail ONLY into the shared
 *       downstream terminal (line isolation); returns the body-root HEAD task id.</li>
 *   <li>{@link #materializeOutputsTx} - write-once the declared outputs at the line ctx (fresh
 *       keys, never a read-modify-write; S3 Object-Lock safe) and register them in
 *       {@code ExecContextVariableState} so an objection clone carries them.</li>
 *   <li>{@link #markLineSkippedTx} - mark the grafted line SKIPPED (terminal) event-free: the
 *       state YAML records SKIPPED (publishes no events) and each affected TaskImpl row is set to
 *       SKIPPED DIRECTLY so NOT ONE dispatcher event fires during the graft.</li>
 * </ol>
 *
 * Error code prefix: {@code 831.}
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextGraftTxService {

    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;
    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final ExecContextVariableStateService execContextVariableStateService;

    /**
     * Stage 1 - CREATE the grafted body FLAT under the target at {@code lineCtxId} (PRE_INIT), wire
     * the tail into the shared terminal only, and return the body-root HEAD task id.
     * Caller MUST hold the ExecContext / Graph / TaskState write locks.
     */
    @Transactional
    public Long createGroupTasksTx(
            ExecContextApiData.SimpleExecContext sec, InternalFunctionData.ExecutionContextData ecd,
            Long targetTaskId, String lineCtxId, String rootProcessCode,
            List<ExecContextGraftService.InputBinding> inputBindings) {

        // 1. Write the bound inputs at the fresh line ctx. The body's tasks declare these as inputs,
        //    so the variables must exist before the sub-branch is created/runnable.
        for (ExecContextGraftService.InputBinding b : inputBindings) {
            variableTxService.createInputVariablesForSubProcess(
                    new VariableData.VariableDataSource(b.value()),
                    sec.execContextId, b.name(), lineCtxId, false);
        }

        // 2. Instantiate the body sub-graph PRE_INIT, parented on the target - the canonical primitive
        //    the splitter itself uses.
        // Snapshot existing task ids so the newly-created grafted head can be identified afterward.
        Set<Long> preExisting = new HashSet<>();
        for (TaskImpl pe : taskRepository.findByExecContextIdReadOnly(sec.execContextId)) {
            preExisting.add(pe.id);
        }

        ExecContextData.GraphAndStates gas = execContextGraphService.prepareGraphAndStates(
                sec.execContextGraphId, sec.execContextTaskStateId);
        List<Long> lastIds = new ArrayList<>();
        taskProducingService.createTasksForSubProcesses(gas, sec, ecd, lineCtxId, targetTaskId, lastIds);

        // LINE ISOLATION - wire this line's tail ONLY into the single shared downstream terminal;
        // ecd.descendants is the target's LIVE direct children, polluted by every earlier grafted line
        // head (each a direct child of the target). Keep only descendants OUTSIDE this line's ctx prefix.
        // The per-vertex ctx is resolved from the DB (a live descendant's TaskVertex.taskContextId is
        // not reliably populated); the filter predicate itself is a pure static for Spring-less unit tests.
        Set<ExecContextData.TaskVertex> terminalDescendants = filterTerminalDescendants(
                ecd.descendants, lineCtxId,
                v -> {
                    TaskImpl dt = taskRepository.findByIdReadOnly(v.taskId);
                    return dt == null ? null : dt.getTaskParamsYaml().task.taskContextId;
                });
        execContextGraphService.createEdges(gas.graph(), lastIds, terminalDescendants);
        execContextGraphService.save(gas);

        Long headId = findHeadTaskId(sec.execContextId, preExisting, rootProcessCode);
        log.info("831.100 grafted {} sub-process(es) at ctx {} under target #{} (head=#{})",
                ecd.subProcesses.size(), lineCtxId, targetTaskId, headId);
        return headId;
    }

    /**
     * Stage 2 - WRITE-ONCE materialize the declared outputs at {@code lineCtxId} (fresh keys, never a
     * read-modify-write - S3 Object-Lock safe) and register the HEAD task's variable state so a clone's
     * collectVariableIds carries them. Caller MUST hold the ExecContext write lock.
     */
    @Transactional
    public void materializeOutputsTx(
            ExecContextApiData.SimpleExecContext sec, Long headTaskId, String lineCtxId,
            List<ExecContextGraftService.OutputMaterialization> outputs) {

        if (outputs.isEmpty()) {
            log.info("831.300 no outputs to materialize at ctx {}", lineCtxId);
            return;
        }
        TaskImpl headTask = taskRepository.findByIdReadOnly(headTaskId);
        if (headTask == null) {
            throw new IllegalStateException("831.310 grafted head task #" + headTaskId
                    + " not found in execContext #" + sec.execContextId);
        }
        TaskParamsYaml tpy = headTask.getTaskParamsYaml();

        List<ExecContextApiData.VariableInfo> infos = new ArrayList<>();
        for (ExecContextGraftService.OutputMaterialization out : outputs) {
            byte[] bytes = out.value();
            Variable v = variableTxService.createInitializedTx(
                    new ByteArrayInputStream(bytes), bytes.length,
                    out.name(), out.name() + ".txt",
                    sec.execContextId, lineCtxId, EnumsApi.VariableType.text);
            infos.add(outputInfo(v.id, out.name()));
        }

        ExecContextApiData.VariableState state = new ExecContextApiData.VariableState();
        state.taskId = headTaskId;
        state.execContextId = sec.execContextId;
        state.taskContextId = lineCtxId;
        state.process = tpy.task.processCode;
        state.functionCode = tpy.task.function != null ? tpy.task.function.code : null;
        state.outputs = infos;
        execContextVariableStateService.registerCreatedTasks(sec.execContextVariableStateId, List.of(state));

        log.info("831.320 materialized+registered {} write-once output(s) for head #{} at ctx {}",
                outputs.size(), headTaskId, lineCtxId);
    }

    /**
     * Stage 3 - mark the grafted line SKIPPED (terminal), event-free. The state YAML records SKIPPED
     * for the seed head + propagated children (publishes no events), then each TaskImpl row is set to
     * SKIPPED DIRECTLY (not via changeTaskState) so NO dispatcher event fires during the graft. The
     * seed head is NOT in updateTaskExecState's returned child list, so it is set explicitly. Caller
     * MUST hold the ExecContext / Graph / TaskState write locks.
     */
    @Transactional
    public void markLineSkippedTx(ExecContextApiData.SimpleExecContext sec, Long headTaskId, String headCtx) {

        ExecContextData.ExecContextDAC dac = execContextGraphService.getExecContextDAC(
                sec.execContextId, sec.execContextGraphId);
        ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                dac, sec.execContextTaskStateId,
                List.of(new TaskData.TaskWithStateAndTaskContextId(
                        headTaskId, EnumsApi.TaskExecState.SKIPPED, headCtx)));

        Set<Long> toSkip = new LinkedHashSet<>();
        toSkip.add(headTaskId);
        for (TaskData.TaskWithState t : status.childrenTasks) {
            toSkip.add(t.taskId);
        }
        final long now = System.currentTimeMillis();
        for (Long taskId : toSkip) {
            TaskSyncService.getWithSyncVoid(taskId, () -> {
                TaskImpl task = taskRepository.findById(taskId).orElseThrow(
                        () -> new IllegalStateException("831.410 grafted branch task #" + taskId
                                + " not found in execContext #" + sec.execContextId));
                task.setExecState(EnumsApi.TaskExecState.SKIPPED.value);
                task.setCompleted(1);
                task.setCompletedOn(now);
                taskRepository.save(task);
            });
        }
        log.info("831.420 marked grafted line SKIPPED (terminal, no dispatch): head #{} + {} descendant(s) at ctx {}",
                headTaskId, toSkip.size() - 1, headCtx);
    }

    /**
     * LINE ISOLATION predicate - keep only the descendants that are OUTSIDE the grafted line's own
     * per-line context prefix (everything up to and including the '#'), i.e. the shared downstream
     * terminal; drop every sibling line head that shares the splitter's per-line prefix. Pure static
     * with a ctx-resolver function so it is unit-testable without a Spring context / DB.
     *
     * @param descendants the target's live direct descendants
     * @param lineCtxId   this graft's fresh line ctx (e.g. "1,2#2")
     * @param ctxResolver resolves a vertex's taskContextId (DB lookup in prod; direct in tests)
     */
    static Set<ExecContextData.TaskVertex> filterTerminalDescendants(
            Set<ExecContextData.TaskVertex> descendants, String lineCtxId,
            Function<ExecContextData.TaskVertex, String> ctxResolver) {
        final int hashIdx = lineCtxId.lastIndexOf('#');
        final String lineCtxPrefix = hashIdx >= 0 ? lineCtxId.substring(0, hashIdx + 1) : null;
        Set<ExecContextData.TaskVertex> out = new LinkedHashSet<>();
        for (ExecContextData.TaskVertex v : descendants) {
            String ctx = ctxResolver.apply(v);
            if (lineCtxPrefix == null || ctx == null || !ctx.startsWith(lineCtxPrefix)) {
                out.add(v);
            }
        }
        return out;
    }

    private Long findHeadTaskId(Long execContextId, Set<Long> preExistingTaskIds, String rootProcessCode) {
        // The grafted head is the newly-created (not pre-existing) task carrying the body-root process
        // code. Identity-by-newness + processCode is robust to how createTasksForSubProcesses derives
        // the ctx (sequential places it at the line ctx; other logics derive it).
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(execContextId)) {
            if (preExistingTaskIds.contains(t.id)) {
                continue;
            }
            if (rootProcessCode.equals(t.getTaskParamsYaml().task.processCode)) {
                return t.id;
            }
        }
        throw new IllegalStateException("831.200 grafted head (process '" + rootProcessCode
                + "') not found among newly created tasks in execContext #" + execContextId);
    }

    private static ExecContextApiData.VariableInfo outputInfo(Long id, String name) {
        ExecContextApiData.VariableInfo vi = new ExecContextApiData.VariableInfo();
        vi.id = id;
        vi.name = name;
        vi.context = EnumsApi.VariableContext.local;
        vi.inited = true;
        vi.nullified = false;
        return vi;
    }
}
