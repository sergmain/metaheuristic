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
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentTxService;
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
 * (025-MHSC-DSL-V2-PLAN, Phase 1). Pure MH
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
 * <p>041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 8: the three writes land in the grafted line's own segment - Stage 1 creates it
 * (its Tasks, their states), Stage 2 adds the variable-state entry to it, Stage 3 marks its line SKIPPED in it. None of
 * them reads or writes the whole-ExecContext graph, task-state or variable-state record, and none touches another
 * segment. The line's join is derived; a live line (RUN_NOW) registers its tails with that join, a line born SKIPPED
 * (PLACE_NOW) never does. Locks: see {@link ExecContextGraftService} - an out-of-band PLACE_NOW holds none.
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

    private final TaskProducingService taskProducingService;
    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final ExecContextSegmentTxService segmentTxService;

    /**
     * Stage 1 - CREATE the grafted body FLAT under the target at {@code lineCtxId} (PRE_INIT), wire
     * the tail into the shared terminal only, and return the body-root HEAD task id.
     * Caller MUST hold the ExecContext / Graph / TaskState write locks.
     * 041 Phase 8: the in-band form - the line goes into its own new segment, and its tail(s) are reported in
     * {@code unwiredTailsOut} for the caller to register with the derived join once its block is complete.
     */
    @Transactional
    public Long createGroupTasksTx(
            ExecContextApiData.SimpleExecContext sec, InternalFunctionData.ExecutionContextData ecd,
            Long targetTaskId, String lineCtxId, String rootProcessCode,
            List<ExecContextGraftService.InputBinding> inputBindings, List<Long> unwiredTailsOut) {
        return createGroupTasksTx(sec, ecd, targetTaskId, lineCtxId, rootProcessCode, inputBindings, unwiredTailsOut,
                new ArrayList<>(), ExecContextSegmentTxService.SegmentStart.OWN, false);
    }

    /**
     * Stage 1, also reporting the ids of the tasks it created into {@code createdTaskIdsOut} (ascending), so a
     * caller looking for one of the line's tasks searches the line rather than the whole ExecContext.
     * Caller MUST hold the ExecContext / Graph / TaskState write locks.
     * 041 Phase 8: {@code segmentStart} is {@code SegmentStart.own(id)} when the line ctx was derived from an allocated
     * segment id (decision 10), else {@code SegmentStart.OWN}; {@code registerTails} registers the line's tails with the
     * derived join in this transaction (out-of-band RUN_NOW). Every tail is reported in {@code unwiredTailsOut}.
     */
    @Transactional
    public Long createGroupTasksTx(
            ExecContextApiData.SimpleExecContext sec, InternalFunctionData.ExecutionContextData ecd,
            Long targetTaskId, String lineCtxId, String rootProcessCode,
            List<ExecContextGraftService.InputBinding> inputBindings, List<Long> unwiredTailsOut,
            List<Long> createdTaskIdsOut, ExecContextSegmentTxService.SegmentStart segmentStart, boolean registerTails) {

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
        // Ids only: the snapshot needs no task's params.
        // 041 Phase 8: no snapshot - it read every Task id of the ExecContext per graft (O(N)). The created Tasks are the
        // line of the new segment, read back below; and the whole-ExecContext graph and task state are no longer loaded.
        List<Long> lastIds = new ArrayList<>();
        // 041: a graft is its own segment (decision 7); its ctx allocation (decision 10) and join registration are Phase 8
        taskProducingService.createTasksForSubProcesses(sec, ecd, lineCtxId, targetTaskId, lastIds, segmentStart);

        // LINE ISOLATION - wire this line's tail ONLY into the single shared downstream terminal;
        // ecd.descendants is the target's LIVE direct children, polluted by every earlier grafted line
        // head (each a direct child of the target). Keep only descendants OUTSIDE this line's ctx prefix.
        // The per-vertex ctx is resolved from the DB (a live descendant's TaskVertex.taskContextId is
        // not reliably populated); the filter predicate itself is a pure static for Spring-less unit tests.
        // (Superseded 2026-09-25: the ctx is the vertex's own. Every vertex is created with its Task's
        // taskContextId - addNewTasksToGraph, fed by TaskProducingService, and the clone rewrite, which copies
        // it - the DOT export always writes it and the import reads it back. So no Task is loaded per
        // descendant any more - one per earlier grafted line - and the resolver is the one the Spring-less
        // tests already drive the filter with.)
        // F1: if this line has NO terminal to wire into at graft time (its target's downstream is wired
        // LATER by the enclosing block - e.g. the target is the sequential chain tail), report the line's
        // tail(s) so the in-band RUN_NOW caller can rejoin them into the enclosing block's downstream
        // (createTasksForSubProcesses -> lastIds). Otherwise createEdges below wires this line's tail into
        // the shared terminal as usual.
        // 041 Phase 8: no edge is written - a line's join is derived from the segments (the Task after the fork in the
        // fork's line, or recursively the enclosing line's join), so line isolation holds by construction and F1 needs
        // no special case. Every tail is reported; registering them with the join is the caller's (in-band: after its
        // block is complete, via lastIds) or this transaction's (registerTails). A PLACE_NOW line is born SKIPPED and is
        // never registered: its join record stays unchanged.
        unwiredTailsOut.addAll(lastIds);
        if (registerTails) {
            segmentTxService.registerLines(sec.execContextId, lastIds);
        }

        final List<Long> created = segmentTxService.lineTaskIds(sec.execContextId, lineCtxId);
        created.sort(Long::compareTo);
        createdTaskIdsOut.addAll(created);

        Long headId = findHeadTaskId(sec.execContextId, created, rootProcessCode);
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
        // 041 Phase 8: the entry goes into the grafted line's segment, not the whole-ExecContext variable-state record
        segmentTxService.addVariableStates(sec.execContextId, lineCtxId, List.of(state));

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

        // 041 Phase 8: the SKIPPED closure of a freshly grafted line is the line from its head to its tail - its join
        // always has a live parent - so only that line's segment is written; no whole-ExecContext record is read.
        Set<Long> toSkip = new LinkedHashSet<>(segmentTxService.markLineSkipped(sec.execContextId, headCtx, headTaskId));
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
     * LINE ISOLATION predicate - keep only the shared downstream terminal; drop every SIBLING LINE HEAD
     * under the same target. Pure static with a ctx-resolver function so it is unit-testable without a
     * Spring context / DB.
     *
     * <p>Sibling-ness is decided by DERIVED PARENT, not by ctx prefix. The prefix test
     * ({@code ctx.startsWith(lineCtxId up to '#')}) only isolates against lines sharing THIS line's body
     * path, which silently assumes a target's children are all one body path. They are not: a graft can
     * attach a line at body path {@code "1,13"} under a target that already carries a line at
     * {@code "1,2"}. Both are direct children of the same target and both are line heads, but the
     * prefixes differ - so a prefix test lets the foreign-path head through as a tail edge. MH's reset
     * is a plain descendant walk, so that stray edge drags the other line's head off terminal and
     * re-runs it. Two line heads are siblings iff they derive up to the SAME parent, which is exactly
     * what the target is; that is the property to test.
     *
     * <p>Kept conservatively: a vertex whose ctx cannot be resolved (null), and any vertex that is not a
     * line ctx at all (no '#') - the shared terminal sits at the target's own level. A {@code lineCtxId}
     * with no '#' is not a line, so nothing is isolated and everything is kept.
     *
     * @param descendants the target's live direct descendants
     * @param lineCtxId   this graft's fresh line ctx (e.g. "1,2#2")
     * @param ctxResolver resolves a vertex's taskContextId (DB lookup in prod; direct in tests)
     */
    static Set<ExecContextData.TaskVertex> filterTerminalDescendants(
            Set<ExecContextData.TaskVertex> descendants, String lineCtxId,
            Function<ExecContextData.TaskVertex, String> ctxResolver) {
        if (lineCtxId.lastIndexOf('#') < 0) {
            return new LinkedHashSet<>(descendants);
        }
        final String lineParentCtxId = ContextUtils.deriveParentTaskContextId(lineCtxId);
        Set<ExecContextData.TaskVertex> out = new LinkedHashSet<>();
        for (ExecContextData.TaskVertex v : descendants) {
            final String ctx = ctxResolver.apply(v);
            if (ctx == null || ctx.lastIndexOf('#') < 0) {
                out.add(v);
                continue;
            }
            if (lineParentCtxId != null && lineParentCtxId.equals(ContextUtils.deriveParentTaskContextId(ctx))) {
                // a sibling line head under the same target - never a tail target
                continue;
            }
            out.add(v);
        }
        return out;
    }

    private Long findHeadTaskId(Long execContextId, List<Long> createdTaskIds, String rootProcessCode) {
        // The grafted head is the newly-created (not pre-existing) task carrying the body-root process
        // code. Identity-by-newness + processCode is robust to how createTasksForSubProcesses derives
        // the ctx (sequential places it at the line ctx; other logics derive it).
        // Only the created tasks are read - the rest of the ExecContext is never loaded or parsed.
        for (Long id : createdTaskIds) {
            TaskImpl t = taskRepository.findByIdReadOnly(id);
            if (t != null && rootProcessCode.equals(t.getTaskParamsYaml().task.processCode)) {
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
