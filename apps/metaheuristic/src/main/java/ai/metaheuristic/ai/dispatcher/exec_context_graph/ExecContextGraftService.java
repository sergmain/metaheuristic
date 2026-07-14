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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DSL v2 - the MH-owned runtime GRAFT primitive (025-MHSC-DSL-V2-PLAN, Phase 1).
 *
 * <p>Plants a compiled body of processes under an existing target task in an ExecContext, in a
 * fresh isolated {@code taskContextId}, as one event-free write under the EC / Graph / TaskState /
 * VariableState sync locks. This is a server-side dispatcher service (a peer of the graph services),
 * NOT an internal function: the graft targets a possibly-FINISHED EC with no running task to host a
 * function (analysis sec 6). It SUBSUMES a consumer's former hand-minted per-item task machinery;
 * Phase 1 is a relocation + generalization of that proven server-side primitive, seal-stripped into MH.
 *
 * <p><b>MH seal.</b> This primitive knows only ExecContext, task, {@code taskContextId}, group, and
 * variable - it has NO awareness of any consumer's domain concepts. It takes a plain ExecContext id
 * and a plain target task id; everything domain-specific stays in the consumer.
 *
 * <p><b>Scope</b> - the FLAT (sequential) body. PLACE_NOW creates the body PRE_INIT and marks it SKIPPED
 * terminal; RUN_NOW (Phase 2) drives the grafted line forward by reusing the reset-driven reopen-and-run
 * primitive (TaskResetService.resetTaskAndExecContext), which also re-expands an inner dynamic
 * subprocess. The body's processContextId is REBASED onto the
 * target via {@link ContextUtils#getCurrTaskContextIdForSubProcesses} at a fresh sibling line ctx
 * (Phase 0 rebase decision), the tasks are materialized PRE_INIT, the declared outputs are written
 * once to fresh keys and registered in {@code ExecContextVariableState}, and the line is marked
 * SKIPPED terminal. No {@code FindUnassignedTasksAndRegisterInQueueTxEvent} fires during the graft.
 *
 * <p>Non-transactional orchestration (SPRING-TX-RULES sec 1/sec 2): resolves context, acquires the
 * four sync locks, and delegates each write to {@link ExecContextGraftTxService}.
 *
 * Error code prefix: {@code 830.}
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextGraftService {

    /** Which half of the primitive runs. PLACE_NOW = graft only, mark SKIPPED terminal (Phase 1).
     *  RUN_NOW = graft then drive forward via resetTaskAndExecContext (Phase 2). */
    public enum Driver { PLACE_NOW, RUN_NOW }

    /** The body to graft. v0 (Phase 1): {@code groupName==null} => resolve the body as the target
     *  task's own sub-processes (a splitter-0-style sub-process borrow). v1 (Phase 5):
     *  a named v6 group looked up by name. */
    public record GroupRef(@Nullable String groupName) {
        public static GroupRef fromTargetSubProcesses() {
            return new GroupRef(null);
        }
    }

    /** An outer value bound to a group formal input; written WRITE-ONCE at the fresh line ctx BEFORE
     *  task creation so the grafted sub-branch is runnable/resolvable. */
    public record InputBinding(String name, String value) {}

    /** A group output pre-materialized WRITE-ONCE at the fresh line ctx (a PLACE_NOW line never runs,
     *  so any output its downstream needs must pre-exist). The consumer passes whatever outputs it
     *  needs here; the MH primitive is agnostic to what they mean. */
    public record OutputMaterialization(String name, byte[] value) {}

    /** The stable re-entry data of one graft: the body-root (HEAD) task id and the fresh line ctx.
     *  {@code resetPointTaskId} is the grafted task the caller nominated as its reset entry (by
     *  function code); null when no reset-point was requested (in-band grafts) or none matched. */
    public record GraftResult(Long headTaskId, String lineCtxId, List<Long> unwiredTails, @Nullable Long resetPointTaskId) {}

    /** The lock-free, tx-free resolve+rebase result shared by the locked public {@link #attachGroup}
     *  path and the in-band dispatcher direct-call path (which already holds the Graph + TaskState
     *  locks and a tx, so it invokes graftTxService directly rather than re-acquiring locks). */
    public record GraftSetup(ExecContextApiData.SimpleExecContext sec,
                             InternalFunctionData.ExecutionContextData ecd, String lineCtxId,
                             String rootProcessCode, Long graphId, Long taskStateId, Long varStateId) {}

    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final InternalFunctionService internalFunctionService;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextGraftTxService graftTxService;
    private final TaskResetService taskResetService;
    private final VariableTxService variableTxService;

    /**
     * Graft {@code groupRef}'s body under {@code targetTaskId} in {@code execContextId}, flat, PLACE_NOW.
     *
     * @param execContextId the EC to graft into (the consumer may hand a clone; MH does not know that)
     * @param targetTaskId  the existing task to attach under; also the body source in v0
     * @param groupRef      the body (v0: {@link GroupRef#fromTargetSubProcesses()})
     * @param inputBindings outer values written write-once at the line ctx before task creation
     * @param outputs       group outputs pre-materialized write-once + registered in variable state
     * @param driver        PLACE_NOW (Phase 1); RUN_NOW arrives in Phase 2
     * @return the grafted HEAD task id + the fresh line ctx
     */
    public GraftResult attachGroup(
            Long execContextId, Long targetTaskId, GroupRef groupRef,
            List<InputBinding> inputBindings, List<OutputMaterialization> outputs, Driver driver) {
        // 025 Phase 7A - back-compat overload: no reset-point nomination.
        return attachGroup(execContextId, targetTaskId, groupRef, inputBindings, outputs, driver, null);
    }

    /**
     * 025 Phase 7A - reset-point-aware overload. {@code resetPointFunctionCode} (opaque to MH) names
     * the grafted task the caller wants back as its reset entry: after the line is created, the task at
     * the fresh line ctx whose function code equals it is returned on {@link GraftResult#resetPointTaskId()}.
     * This is the O8 reset-point handle the graft primitive captures at graft time.
     */
    public GraftResult attachGroup(
            Long execContextId, Long targetTaskId, GroupRef groupRef,
            List<InputBinding> inputBindings, List<OutputMaterialization> outputs, Driver driver,
            @Nullable String resetPointFunctionCode) {

        TxUtils.checkTxNotExists();
        if (driver == Driver.RUN_NOW && !outputs.isEmpty()) {
            throw new IllegalStateException("830.020 RUN_NOW drives the grafted line, whose tasks produce their own "
                    + "outputs; pass no OutputMaterialization for RUN_NOW (materialization is a PLACE_NOW concern)");
        }
        GraftSetup s = graftSetup(execContextId, targetTaskId, groupRef);
        final ExecContextApiData.SimpleExecContext sec = s.sec();
        final InternalFunctionData.ExecutionContextData ecd = s.ecd();
        final String lineCtxId = s.lineCtxId();
        final String rootProcessCode = s.rootProcessCode();
        final Long graphId = s.graphId();
        final Long taskStateId = s.taskStateId();
        final Long varStateId = s.varStateId();

        // ---- Stage 1: CREATE the grafted line PRE_INIT at the fresh isolated ctx; wire the tail ONLY
        //      into the shared downstream terminal (line isolation). createTasksForSubProcesses leaves
        //      it PRE_INIT, a state neither the dispatcher nor a test driver advances on its own. ----
        AtomicReference<Long> headRef = new AtomicReference<>();
        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(graphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoidForCreation(taskStateId, () ->
                                headRef.set(graftTxService.createGroupTasksTx(
                                        sec, ecd, targetTaskId, lineCtxId, rootProcessCode, inputBindings, new ArrayList<>())))));
        Long headTaskId = headRef.get();

        // ---- Stage 2: WRITE-ONCE materialize the declared outputs at the line ctx + register
        //      ExecContextVariableState so a later clone carries them (no 179.120 / 171.520). ----
        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(graphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoidForCreation(taskStateId, () ->
                                ExecContextVariableStateSyncService.getWithSyncVoid(varStateId, () ->
                                        graftTxService.materializeOutputsTx(sec, headTaskId, lineCtxId, outputs)))));

        // ---- Stage 3 (driver). PLACE_NOW marks the grafted line SKIPPED terminal, event-free (no
        //      dispatcher kick during the graft) - a later reset reopens+runs it. RUN_NOW instead
        //      DRIVES the line forward by REUSING the reset-driven reopen-and-run primitive
        //      (TaskResetService.resetTaskAndExecContext: EC FINISHED->STARTED, head INIT, descendants
        //      PRE_INIT + inner dynamic-subprocess re-expansion, one scheduler kick) - the run half is
        //      reuse, not new code. It acquires its own EC/Graph/TaskState locks (called lock-free here). ----
        switch (driver) {
            case PLACE_NOW -> ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                    ExecContextGraphSyncService.getWithSyncVoid(graphId, () ->
                            ExecContextTaskStateSyncService.getWithSyncVoidForCreation(taskStateId, () ->
                                    graftTxService.markLineSkippedTx(sec, headTaskId, lineCtxId))));
            case RUN_NOW -> taskResetService.resetTaskAndExecContext(execContextId, headTaskId);
        }

        log.info("830.200 attachGroup {}: grafted flat body under target #{} at ctx {} (head=#{}, {} output(s))",
                driver, targetTaskId, lineCtxId, headTaskId, outputs.size());
        Long resetPointTaskId = resolveResetPointTaskId(execContextId, lineCtxId, resetPointFunctionCode);
        return new GraftResult(headTaskId, lineCtxId, List.of(), resetPointTaskId);
    }

    /**
     * 025 Phase 7A - MH-generic reset-point resolver. Scans the grafted line for the task whose
     * FUNCTION code matches the caller-supplied {@code resetPointFunctionCode} (an opaque code the
     * consumer nominates; MH attaches no meaning to it). Returns null when no code was requested or
     * none matched (the caller decides whether absence is an error).
     */
    @Nullable
    private Long resolveResetPointTaskId(Long execContextId, String lineCtxId,
                                         @Nullable String resetPointFunctionCode) {
        if (resetPointFunctionCode == null) {
            return null;
        }
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(execContextId)) {
            TaskParamsYaml tpy = t.getTaskParamsYaml();
            if (!lineCtxId.equals(tpy.task.taskContextId)) {
                continue;
            }
            String fnCode = tpy.task.function != null ? tpy.task.function.code : null;
            if (resetPointFunctionCode.equals(fnCode)) {
                return t.id;
            }
        }
        return null;
    }

    /**
     * Resolve the body to graft and compute the fresh isolated line ctx - the lock-free, tx-free
     * setup shared by {@link #attachGroup} and the in-band dispatcher path. Reads only (findById /
     * findByIdReadOnly), so it is valid both tx-free (public attachGroup) and inside a tx (in-band).
     */
    public GraftSetup graftSetup(Long execContextId, Long targetTaskId, GroupRef groupRef) {
        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec == null) {
            throw new IllegalStateException("830.040 ExecContext #" + execContextId + " not found");
        }
        ExecContextApiData.SimpleExecContext sec = ec.asSimple();

        TaskImpl targetTask = taskRepository.findByIdReadOnly(targetTaskId);
        if (targetTask == null) {
            throw new IllegalStateException("830.060 target task #" + targetTaskId + " not found in execContext #" + execContextId);
        }
        TaskParamsYaml targetTpy = targetTask.getTaskParamsYaml();

        // Resolve the body to graft. v0 (groupName==null): the target task's own sub-processes
        // (a splitter-0-style sub-process borrow). v1 / Phase 5 (groupName!=null): the
        // first-class v6 group looked up by name in the EC's params - same downstream graft, only
        // the body source differs (resolveGroupBody yields the identical ExecutionContextData shape).
        final InternalFunctionData.ExecutionContextData ecd;
        if (groupRef.groupName() != null) {
            ecd = resolveGroupBody(sec, targetTaskId, groupRef.groupName(),
                    ContextUtils.getProcessContextId(ContextUtils.getLevel(targetTpy.task.taskContextId)));
        }
        else {
            ecd = internalFunctionService.getSubProcesses(sec, targetTpy, targetTaskId);
            if (ecd.internalFunctionProcessingResult.processing != Enums.InternalFunctionProcessing.ok) {
                throw new IllegalStateException("830.100 getSubProcesses failed for target #" + targetTaskId + ": "
                        + ecd.internalFunctionProcessingResult.processing);
            }
            if (ecd.subProcesses.isEmpty()) {
                throw new IllegalStateException("830.120 target #" + targetTaskId + " has no sub-processes to graft");
            }
        }
        // Phase 1 = FLAT / sequential only. 'and' (parallel) and or/race bodies are out of scope (O7).
        // createTasksForSubProcesses places sequential sub-processes AT the line ctx, which the flat
        // graft relies on; other logics derive a different ctx.
        if (ecd.process.logic != EnumsApi.SourceCodeSubProcessLogic.sequential) {
            throw new IllegalStateException("830.140 Phase 1 supports only 'sequential' (flat) group bodies; target #"
                    + targetTaskId + " has logic=" + ecd.process.logic);
        }
        // The body root is the first sub-process; its processCode is the grafted line HEAD's identity.
        String rootProcessCode = ecd.subProcesses.get(0).process;

        // ---- Phase-0 rebase (FLAT regime): body-root level under the target, fresh sibling line ctx.
        //      The rebase preserves the 1:1 invariant so deriveParentTaskContextId walks a grafted task
        //      back to the target and the subtree is a normal nested region. ----
        String base = ContextUtils.getCurrTaskContextIdForSubProcesses(
                targetTpy.task.taskContextId, ecd.subProcesses.get(0).processContextId);
        String lineCtxId = ContextUtils.nextSiblingTaskContextId(base, collectCtxIds(execContextId));

        final Long graphId = ec.execContextGraphId;
        final Long taskStateId = ec.execContextTaskStateId;
        final Long varStateId = ec.execContextVariableStateId;
        return new GraftSetup(sec, ecd, lineCtxId, rootProcessCode, graphId, taskStateId, varStateId);
    }

    /**
     * In-band graft expansion (PLACE_NOW) for the dispatcher's task-production path. The caller MUST
     * already hold the Graph + TaskState write locks and be inside a tx (the createTasksForSubProcesses
     * contract); this invokes graftTxService DIRECTLY (no getWithSync wrapper) so the writes join the
     * caller's tx under the locks already present - the same pattern a splitter uses to call its own
     * *TxService when the locks are already applied. Lays the named group as a fresh sibling SKIPPED
     * line under {@code targetTaskId} - the identical shape attachGroup(PLACE_NOW) produces, minus the
     * lock/tx wrappers (they are the caller's). Reopen-and-run of the SKIPPED line stays a later
     * reset/driver concern, so no scheduler kick fires here.
     */
    /**
     * 032 - resolve an in-band graft's authored {@code bind(...)} input names into write-once
     * {@link InputBinding}s. The i-th authored enclosing var name ({@code graft.inputBindings}) is mapped
     * POSITIONALLY onto the group's i-th declared formal input ({@code group.inputs}); its current value is
     * read at the TARGET task's context (ancestry walk). The pair (formalName, value) is later written
     * write-once at the fresh line ctx by {@code createGroupTasksTx}, so the grafted body resolves its formal
     * inputs by name - INCLUDING a rebind (formal name != enclosing name), which is what a per-level depth
     * counter needs (bind {@code nextDepth} onto the child's {@code depth}).
     */
    public List<InputBinding> resolveInBandInputBindings(
            Long execContextId, String resolutionTaskContextId, ExecContextParamsYaml.Graft graft) {
        if (graft.inputBindings.isEmpty()) {
            return List.of();
        }
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            throw new IllegalStateException("01.830.300 execContext #" + execContextId + " not found");
        }
        ExecContextParamsYaml.Group group = null;
        for (ExecContextParamsYaml.Group g : ec.getExecContextParamsYaml().groups) {
            if (graft.groupName.equals(g.name)) {
                group = g;
                break;
            }
        }
        if (group == null) {
            throw new IllegalStateException("01.830.310 group '" + graft.groupName + "' not found in execContext #" + execContextId);
        }
        if (graft.inputBindings.size() > group.inputs.size()) {
            throw new IllegalStateException("01.830.320 graft '" + graft.groupName + "' binds " + graft.inputBindings.size()
                    + " input(s) but group declares only " + group.inputs.size());
        }
        List<InputBinding> result = new ArrayList<>();
        for (int i = 0; i < graft.inputBindings.size(); i++) {
            final String enclosingName = graft.inputBindings.get(i);
            final String formalName = group.inputs.get(i).name;
            Variable v = variableTxService.findVariableInAllInternalContexts(enclosingName, resolutionTaskContextId, execContextId);
            if (v == null) {
                throw new IllegalStateException("01.830.340 graft '" + graft.groupName + "' bind input '" + enclosingName
                        + "' not resolvable at ctx " + resolutionTaskContextId + " of execContext #" + execContextId);
            }
            final String value = variableTxService.getVariableDataAsString(v.id);
            result.add(new InputBinding(formalName, value));
        }
        return result;
    }

    /**
     * When an in-band graft is driven by a dynamic {@code mh.batch-line-splitter} (its target task), the
     * splitter has already materialized its per-line output variable at the CURRENT (per-line) task ctx.
     * Because the graft lays its body at a fresh ISOLATED sibling line ctx, a sibling cannot resolve that
     * per-line variable up the ancestry - so a grafted head that consumes it by name (e.g. mhdg-rg
     * store-req reading {@code reqJson}) would fail with 179.120. This reproduces, for the graft path, the
     * v1 binding that direct sub-process expansion did implicitly: read the splitter's per-line output at
     * {@code currTaskContextId} and carry it as a write-once {@link InputBinding} into the grafted line ctx.
     * Returns null when the target is not a dynamic splitter (no {@code output-variable} meta) or the
     * per-line variable is not (yet) resolvable.
     */
    @Nullable
    public InputBinding resolveEnclosingDynamicSplitterBinding(Long execContextId, Long targetTaskId, String currTaskContextId) {
        TaskImpl targetTask = taskRepository.findByIdReadOnly(targetTaskId);
        if (targetTask == null) {
            return null;
        }
        final String targetProcessCode = targetTask.getTaskParamsYaml().task.processCode;
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            return null;
        }
        ExecContextParamsYaml.Process p = ec.getExecContextParamsYaml().findProcess(targetProcessCode);
        if (p == null) {
            return null;
        }
        // a dynamic batch-line-splitter declares its per-line output via the 'output-variable' meta
        final String outputVar = MetaUtils.getValue(p.metas, "output-variable");
        if (S.b(outputVar)) {
            return null;
        }
        // read the per-line output at EXACTLY currTaskContextId - NOT via an ancestry walk. After the DSL v2
        // migration dropped the per-level {L} suffix every level shares this single name (e.g. reqJson), so an
        // ancestry walk from the per-line ctx could return a same-named ANCESTOR (its per-line row is written in
        // the same tx and is not yet in the async ExecContextVariableState), masking the exact per-line value -
        // which made every grafted rung store the same content.
        Variable v = variableTxService.findVariableByExactTaskContextId(outputVar, currTaskContextId, execContextId);
        if (v == null) {
            return null;
        }
        final String value = variableTxService.getVariableDataAsString(v.id);
        return new InputBinding(outputVar, value);
    }

    public GraftResult attachGroupInBandPlaceNow(Long execContextId, Long targetTaskId, String groupName,
                                                 List<InputBinding> inputBindings) {
        TxUtils.checkTxExists();
        GraftSetup s = graftSetup(execContextId, targetTaskId, new GroupRef(groupName));
        Long head = graftTxService.createGroupTasksTx(
                s.sec(), s.ecd(), targetTaskId, s.lineCtxId(), s.rootProcessCode(), inputBindings, new ArrayList<>());
        graftTxService.markLineSkippedTx(s.sec(), head, s.lineCtxId());
        log.info("830.220 in-band graft PLACE_NOW: group '{}' under target #{} at ctx {} (head=#{})",
                groupName, targetTaskId, s.lineCtxId(), head);
        return new GraftResult(head, s.lineCtxId(), List.of(), null);
    }

    /**
     * In-band graft expansion (RUN_NOW) for the dispatcher's task-production path. Same lock/tx contract
     * as {@link #attachGroupInBandPlaceNow}: the caller already holds Graph + TaskState locks and a tx,
     * so graftTxService is invoked directly. Unlike the out-of-band RUN_NOW (which resets a FINISHED EC),
     * an in-band RUN_NOW does NOT reset: the EC is still producing, so the grafted line is laid live
     * (PRE_INIT, wired as a child of the target and into the shared terminal) and the normal dispatcher
     * runs it when the target completes. No markLineSkippedTx, no scheduler kick.
     */
    public GraftResult attachGroupInBandRunNow(Long execContextId, Long targetTaskId, String groupName,
                                               List<InputBinding> inputBindings) {
        TxUtils.checkTxExists();
        GraftSetup s = graftSetup(execContextId, targetTaskId, new GroupRef(groupName));
        List<Long> unwiredTails = new ArrayList<>();
        Long head = graftTxService.createGroupTasksTx(
                s.sec(), s.ecd(), targetTaskId, s.lineCtxId(), s.rootProcessCode(), inputBindings, unwiredTails);
        log.info("830.240 in-band graft RUN_NOW: group '{}' under target #{} at ctx {} (head=#{}, live PRE_INIT)",
                groupName, targetTaskId, s.lineCtxId(), head);
        return new GraftResult(head, s.lineCtxId(), unwiredTails, null);
    }

    /**
     * Phase 5 (O5) - by-name resolution of a first-class v6 {@link ExecContextParamsYaml.Group}.
     * Looks the group up by name in the EC's params and materializes the SAME
     * {@link InternalFunctionData.ExecutionContextData} shape the v0 by-reference path derives from
     * {@link InternalFunctionService#getSubProcesses}, so the downstream graft is identical - only
     * the body SOURCE differs:
     * <ul>
     *   <li>the group's body processes become the sub-processes (identity = {@code processCode},
     *       ctx = {@code internalContextId}) - what {@code createTasksForSubProcesses} reads;</li>
     *   <li>a synthetic {@code sequential} parent carries the FLAT logic (O7: sequential-only in v2.0);
     *       it is an anchor for the logic check, never itself created;</li>
     *   <li>a body-scoped {@link ExecContextParamsYaml} makes the body processes resolvable via
     *       {@code findProcess} and carries {@code inline} + {@code clean} (the only params fields the
     *       task-creation path reads);</li>
     *   <li>{@code descendants} stays the TARGET's live direct children - the body source does not
     *       change what the target flows into (line isolation into the shared terminal is unchanged).</li>
     * </ul>
     */
    private InternalFunctionData.ExecutionContextData resolveGroupBody(
            ExecContextApiData.SimpleExecContext sec, Long targetTaskId, String groupName, String targetProcessCtx) {

        ExecContextParamsYaml.Group group = null;
        for (ExecContextParamsYaml.Group g : sec.paramsYaml.groups) {
            if (groupName.equals(g.name)) {
                group = g;
                break;
            }
        }
        if (group == null) {
            throw new IllegalStateException("830.160 group '" + groupName + "' not found in execContext #" + sec.execContextId);
        }
        if (group.body.isEmpty()) {
            throw new IllegalStateException("830.180 group '" + groupName + "' has an empty body");
        }

        // Body processes -> sub-process vertices (identity = processCode, ctx = internalContextId).
        List<ExecContextApiData.ProcessVertex> subProcesses = new ArrayList<>();
        long vid = 0;
        for (ExecContextParamsYaml.Process p : group.body) {
            // Phase 6a option 2 (A): lay only ROOT-level body processes (ctx == the group root). A deeper-ctx
            // body process is enclosed by an internal function within the body; it is that function's sub-process
            // and is expanded at RUNTIME when the enclosing function runs (getSubProcesses, part B) - mirroring
            // how produceTasksForExecContext defers internal-function sub-processes. Without this, a nested graft
            // would be laid as a flat sibling at instantiation and hit the fail-fast (375.130).
            // A group with a NULL root ctx has no declared nesting namespace (a synthetic/flat v0-style
            // body, e.g. one minted from a target's own sub-processes) - lay its whole body as-is.
            if (group.internalContextId != null && !p.internalContextId.equals(group.internalContextId)) {
                continue;
            }
            // DSL v2: rebase the body's own context namespace UNDER the target's process context so a grafted
            // by-name group NESTS under the target - deriveParentTaskContextId then walks a grafted task back
            // to the target (needed for variable resolution + reset-subtree identity). The group root context
            // is a globally-unique counter value (shared contextIdSupplier), so 'targetProcessCtx,groupCtx' is
            // a fresh path; multiple grafts of one group differ by the #instance the caller (graftSetup) assigns.
            String rebasedCtx = targetProcessCtx + ContextUtils.CONTEXT_DIGIT_SEPARATOR + p.internalContextId;
            subProcesses.add(new ExecContextApiData.ProcessVertex(vid++, p.processCode, rebasedCtx));
        }

        // Synthetic sequential parent - the graft is FLAT/sequential (O7); it is an anchor, never created.
        ExecContextParamsYaml.Process parent = new ExecContextParamsYaml.Process();
        parent.processCode = "group:" + groupName;
        parent.logic = EnumsApi.SourceCodeSubProcessLogic.sequential;

        // Body-scoped params: findProcess must resolve the body processes; inline + clean carry over.
        ExecContextParamsYaml bodyParams = new ExecContextParamsYaml();
        bodyParams.clean = sec.paramsYaml.clean;
        bodyParams.variables.inline.putAll(sec.paramsYaml.variables.inline);
        bodyParams.processes.addAll(group.body);

        InternalFunctionData.ExecutionContextData ecd = new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok));
        ecd.subProcesses = subProcesses;
        ecd.process = parent;
        ecd.execContextParamsYaml = bodyParams;
        // descendants: the TARGET's live direct children (same as v0) - the line's tail wires into the
        // single shared downstream terminal; body source does not change what the target flows into.
        ecd.descendants = execContextGraphService.findDirectDescendants(sec.execContextGraphId, targetTaskId);
        return ecd;
    }

    private Set<String> collectCtxIds(Long execContextId) {
        Set<String> ctxIds = new HashSet<>();
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(execContextId)) {
            String ctxId = t.getTaskParamsYaml().task.taskContextId;
            if (ctxId != null) {
                ctxIds.add(ctxId);
            }
        }
        return ctxIds;
    }
}
