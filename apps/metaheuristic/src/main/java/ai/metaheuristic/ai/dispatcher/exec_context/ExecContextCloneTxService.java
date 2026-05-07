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
package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-row insert/update helpers for {@link ExecContextCloneService}.
 *
 * Every public method runs in its own {@code REQUIRES_NEW} transaction so the
 * orchestrator can run multiple inserts/updates from outside any enclosing
 * transactional scope, including from a fixed-size thread pool when cloning
 * variables in parallel.
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextCloneTxService {

    private final ExecContextRepository execContextRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final TaskRepository taskRepository;
    private final VariableRepository variableRepository;

    /**
     * Insert the new ExecContext row in {@code state=CLONING}. Graph / task-state /
     * variable-state pointers are filled in later by
     * {@link #updateExecContextChildPointers}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecContextImpl insertNewExecContext(ExecContextImpl source) {
        ExecContextImpl dst = new ExecContextImpl();
        dst.sourceCodeId = source.sourceCodeId;
        dst.companyId = source.companyId;
        dst.accountId = source.accountId;
        dst.createdOn = System.currentTimeMillis();
        dst.completedOn = null;
        dst.valid = source.valid;
        dst.state = EnumsApi.ExecContextState.CLONING.code;
        dst.execContextGraphId = 0L;          // placeholder, rewritten in stage 2
        dst.execContextTaskStateId = 0L;      // placeholder, rewritten in stage 2
        dst.execContextVariableStateId = 0L;  // placeholder, rewritten in stage 2
        dst.rootExecContextId = source.rootExecContextId;
        dst.latch = source.latch == null ? "" : source.latch;
        dst.setParams(source.getParams());
        return execContextRepository.save(dst);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecContextGraph insertNewGraph(ExecContextGraph source, Long newExecContextId) {
        ExecContextGraph dst = new ExecContextGraph();
        dst.execContextId = newExecContextId;
        dst.createdOn = System.currentTimeMillis();
        dst.setParams(source.getParams());
        return execContextGraphRepository.save(dst);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecContextTaskState insertNewTaskState(ExecContextTaskState source, Long newExecContextId) {
        ExecContextTaskState dst = new ExecContextTaskState();
        dst.execContextId = newExecContextId;
        dst.createdOn = System.currentTimeMillis();
        dst.setParams(source.getParams());
        return execContextTaskStateRepository.save(dst);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecContextVariableState insertNewVariableState(ExecContextVariableState source, Long newExecContextId) {
        ExecContextVariableState dst = new ExecContextVariableState();
        dst.execContextId = newExecContextId;
        dst.createdOn = System.currentTimeMillis();
        dst.setParams(source.getParams());
        return execContextVariableStateRepository.save(dst);
    }

    /**
     * Insert one new Task. Returns the new (oldId, newId) pair. The caller
     * places this pair into the per-clone {@code ConcurrentHashMap<oldTaskId,
     * newTaskId>} so Stage 2 can rewrite references.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskClonedIds insertNewTask(Long sourceTaskId, Long newExecContextId) {
        // Backward-compat overload: no variable/task rewrite (caller has not provided maps).
        return insertNewTask(sourceTaskId, newExecContextId, java.util.Map.of(), java.util.Map.of());
    }

    public TaskClonedIds insertNewTask(Long sourceTaskId, Long newExecContextId,
                                       java.util.Map<Long, Long> variableIdMap) {
        // Backward-compat overload: variable rewrite only (no parentTaskIds remap).
        return insertNewTask(sourceTaskId, newExecContextId, variableIdMap, java.util.Map.of());
    }

    /**
     * Phase 13.G.5 — clone a task into the new ExecContext, rewriting:
     *   (a) variable IDs in TaskParamsYaml.inputs/outputs from source-EC space
     *       to clone-EC space (else {@code resetTask} fails 171.520 cross-EC),
     *   (b) parent task IDs in TaskParamsYaml.task.init.parentTaskIds (else
     *       {@code TaskVariableInitTxService} fails 179.240 vertex-not-found
     *       when looking up parents in the cloned graph).
     *
     * Pass an empty taskIdMap to skip (b); pass an empty variableIdMap to skip (a).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskClonedIds insertNewTask(Long sourceTaskId, Long newExecContextId,
                                       java.util.Map<Long, Long> variableIdMap,
                                       java.util.Map<Long, Long> taskIdMapForParentRewrite) {
        TaskImpl src = taskRepository.findByIdReadOnly(sourceTaskId);
        if (src == null) {
            throw new IllegalStateException("Source task not found: " + sourceTaskId);
        }
        TaskImpl dst = new TaskImpl();
        // Phase 13.G.5 — preserve execState/completed/resultReceived/completedOn so
        // FINISHED tasks in the source remain FINISHED in the clone. Without this,
        // every cloned task starts in NONE and MH re-executes the entire DAG from
        // scratch in the clone, which (a) re-fires upstream tasks like open-stage
        // (now correctly idempotent) and (b) re-writes already-inited output
        // Variables, hitting the 171.100 immutability guard. The orchestrator's
        // explicit reset of a specific task is the only state mutation that should
        // happen post-clone; everything else stays as the source left it.
        // coreId/assignedOn/accessByProcessorOn ARE reset because those are processor-
        // assignment metadata and would falsely point at processors that never saw
        // the cloned task.
        dst.coreId = null;
        dst.assignedOn = null;
        dst.accessByProcessorOn = null;
        dst.completedOn = src.completedOn;
        dst.completed = src.completed;
        dst.functionExecResults = src.functionExecResults;
        dst.execContextId = newExecContextId;
        dst.execState = src.execState;
        dst.resultReceived = src.resultReceived;
        dst.resultResourceScheduledOn = src.resultResourceScheduledOn;
        if (variableIdMap.isEmpty() && taskIdMapForParentRewrite.isEmpty()) {
            dst.setParams(src.getParams());
        } else {
            String rewritten = rewriteTaskParamsIds(src.getParams(), variableIdMap, taskIdMapForParentRewrite);
            dst.setParams(rewritten);
        }
        TaskImpl saved = taskRepository.save(dst);
        log.info("Phase13.G.5 insertNewTask: srcTaskId={} -> clonedTaskId={} in clonedEC={}",
                sourceTaskId, saved.id, newExecContextId);
        return new TaskClonedIds(sourceTaskId, saved.id);
    }

    /**
     * Phase 13.G.5 — rewrite the variable IDs and parent task IDs in a
     * TaskParamsYaml string. Inputs/outputs variable IDs are remapped via
     * {@code variableIdMap}; {@code task.init.parentTaskIds} are remapped via
     * {@code taskIdMap}. Entries not present in either map are left untouched.
     */
    private static String rewriteTaskParamsIds(String paramsYaml,
                                               java.util.Map<Long, Long> variableIdMap,
                                               java.util.Map<Long, Long> taskIdMap) {
        ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy =
                ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils.UTILS.to(paramsYaml);
        if (tpy == null || tpy.task == null) {
            return paramsYaml;
        }
        for (ai.metaheuristic.commons.yaml.task.TaskParamsYaml.InputVariable in : tpy.task.inputs) {
            Long mapped = variableIdMap.get(in.id);
            if (mapped != null) {
                in.id = mapped;
            }
        }
        for (ai.metaheuristic.commons.yaml.task.TaskParamsYaml.OutputVariable out : tpy.task.outputs) {
            Long mapped = variableIdMap.get(out.id);
            if (mapped != null) {
                out.id = mapped;
            }
        }
        if (tpy.task.init != null && tpy.task.init.parentTaskIds != null) {
            java.util.List<Long> remapped = new java.util.ArrayList<>(tpy.task.init.parentTaskIds.size());
            for (Long pid : tpy.task.init.parentTaskIds) {
                Long mapped = taskIdMap.get(pid);
                remapped.add(mapped != null ? mapped : pid);
            }
            tpy.task.init.parentTaskIds = remapped;
        }
        return ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils.UTILS.toString(tpy);
    }

    /**
     * Phase 13.G.5 — pass A of the clone: insert a minimal placeholder TaskImpl row
     * to allocate a row id. The row is filled in pass B. Returns the allocated id.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long preAllocateClonedTask(Long newExecContextId) {
        TaskImpl placeholder = new TaskImpl();
        placeholder.execContextId = newExecContextId;
        placeholder.execState = EnumsApi.TaskExecState.NONE.value;
        placeholder.completed = 0;
        placeholder.resultReceived = 0;
        placeholder.resultResourceScheduledOn = 0L;
        placeholder.setParams("");
        TaskImpl saved = taskRepository.save(placeholder);
        // Force a flush so the INSERT hits the DB inside this TX before commit.
        log.info("Phase13.G.5 preAllocateClonedTask: pre-allocated clonedTaskId={} in clonedEC={} (saved.id={}, version={})",
                saved.id, newExecContextId, saved.id, saved.version);
        return saved.id;
    }

    /**
     * Phase 13.G.5 — pass B of the clone: fill the pre-allocated row at
     * {@code clonedTaskId} with the source task's content (state, params),
     * rewriting variable IDs in inputs/outputs via {@code variableIdMap} and
     * parent task IDs in {@code task.init.parentTaskIds} via {@code taskIdMap}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fillClonedTask(Long sourceTaskId, Long clonedTaskId, Long newExecContextId,
                               java.util.Map<Long, Long> variableIdMap,
                               java.util.Map<Long, Long> taskIdMap) {
        TaskImpl src = taskRepository.findByIdReadOnly(sourceTaskId);
        if (src == null) {
            throw new IllegalStateException("Phase13.G.5 fillClonedTask: source task #" + sourceTaskId + " not found");
        }
        TaskImpl dst = taskRepository.findById(clonedTaskId).orElse(null);
        if (dst == null) {
            throw new IllegalStateException("Phase13.G.5 fillClonedTask: pre-allocated cloned task #" + clonedTaskId + " not found");
        }
        // Preserve execState/completion so FINISHED tasks in the source remain FINISHED
        // in the clone (see insertNewTask javadoc).
        dst.coreId = null;
        dst.assignedOn = null;
        dst.accessByProcessorOn = null;
        dst.completedOn = src.completedOn;
        dst.completed = src.completed;
        dst.functionExecResults = src.functionExecResults;
        dst.execContextId = newExecContextId;
        dst.execState = src.execState;
        dst.resultReceived = src.resultReceived;
        dst.resultResourceScheduledOn = src.resultResourceScheduledOn;

        String rewritten = rewriteTaskParamsIds(src.getParams(), variableIdMap, taskIdMap);
        dst.setParams(rewritten);
        taskRepository.save(dst);
        log.info("Phase13.G.5 fillClonedTask: filled clonedTaskId={} (from srcTaskId={}) execState={}",
                clonedTaskId, sourceTaskId, dst.execState);
    }

    /**
     * Phase 13.G.5 — pass 2 of the clone: rewrite an already-cloned task's
     * {@code TaskParamsYaml.task.init.parentTaskIds} from source-EC space to
     * clone-EC space. Run after all task rows are inserted and the source→clone
     * task ID map is complete. Only the parent-IDs are rewritten; variable IDs
     * were already rewritten in pass 1.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rewriteClonedTaskParentIds(Long clonedTaskId, java.util.Map<Long, Long> taskIdMap) {
        // Use findByIdReadOnly (a JPQL @Query) to bypass any L2-cache miss after the
        // pass-1 REQUIRES_NEW insert. The row is committed but the L2 cache may not
        // have it yet on the read path.
        TaskImpl t = taskRepository.findByIdReadOnly(clonedTaskId);
        if (t == null) {
            log.warn("Phase13.G.5 rewriteClonedTaskParentIds: cloned task #{} not found via findByIdReadOnly", clonedTaskId);
            return;
        }
        ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy =
                ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils.UTILS.to(t.getParams());
        if (tpy == null || tpy.task == null || tpy.task.init == null
                || tpy.task.init.parentTaskIds == null
                || tpy.task.init.parentTaskIds.isEmpty()) {
            return;
        }
        boolean changed = false;
        java.util.List<Long> remapped = new java.util.ArrayList<>(tpy.task.init.parentTaskIds.size());
        for (Long pid : tpy.task.init.parentTaskIds) {
            Long mapped = taskIdMap.get(pid);
            if (mapped != null && !mapped.equals(pid)) {
                changed = true;
                remapped.add(mapped);
            } else {
                remapped.add(pid);
            }
        }
        if (changed) {
            tpy.task.init.parentTaskIds = remapped;
            t.setParams(ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils.UTILS.toString(tpy));
            taskRepository.save(t);
            log.info("Phase13.G.5 rewriteClonedTaskParentIds: clonedTask=#{} parentTaskIds rewritten to {}",
                    clonedTaskId, remapped);
        } else {
            log.info("Phase13.G.5 rewriteClonedTaskParentIds: clonedTask=#{} no change (parents already mapped or not in map): {}",
                    clonedTaskId, tpy.task.init.parentTaskIds);
        }
    }

    /**
     * Clone one Variable row. Variable's blob (variableBlobId) is shared, not
     * copied. Returns the new (oldVarId, newVarId) pair so the caller can map
     * them in {@code ConcurrentHashMap<oldVarId, newVarId>}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VariableClonedIds insertNewVariable(Long sourceVariableId, Long newExecContextId) {
        Variable src = variableRepository.findById(sourceVariableId).orElse(null);
        if (src == null) {
            throw new IllegalStateException("Source variable not found: " + sourceVariableId);
        }
        Variable dst = new Variable();
        // Clone Variables verbatim. Source-EC immutability is the contract: every
        // attribute of the source's Variable rows is preserved on the clone, including
        // inited/nullified/variableBlobId. Blobs are SHARED (not copied) — same
        // variableBlobId means both rows reference the same content. Subsequent task
        // resets in the clone will reset OUTPUT Variables to inited=false before the
        // re-running task writes them; that's the existing per-task-reset path.
        dst.inited = src.inited;
        dst.nullified = src.nullified;
        dst.variableBlobId = src.variableBlobId;
        dst.name = src.name;
        dst.execContextId = newExecContextId;
        dst.taskContextId = src.taskContextId;
        dst.uploadTs = src.uploadTs;
        dst.filename = src.filename;
        dst.setParams(src.getParams());
        Variable saved = variableRepository.save(dst);
        return new VariableClonedIds(sourceVariableId, saved.id);
    }

    /**
     * Stage 2: persist rewritten params for graph / task-state / variable-state
     * and flip the new ExecContext to FINISHED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeRewrittenChildren(Long newExecContextId,
                                       Long newGraphId, String newGraphParams,
                                       Long newTaskStateId, String newTaskStateParams,
                                       Long newVariableStateId, String newVariableStateParams) {
        ExecContextGraph g = execContextGraphRepository.findById(newGraphId).orElseThrow();
        g.setParams(newGraphParams);
        execContextGraphRepository.save(g);

        ExecContextTaskState ts = execContextTaskStateRepository.findById(newTaskStateId).orElseThrow();
        ts.setParams(newTaskStateParams);
        execContextTaskStateRepository.save(ts);

        ExecContextVariableState vs = execContextVariableStateRepository.findById(newVariableStateId).orElseThrow();
        vs.setParams(newVariableStateParams);
        execContextVariableStateRepository.save(vs);
    }

    /**
     * Stage 1 follow-up: once the graph / task-state / variable-state rows
     * have ids, point the new ExecContext at them.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateExecContextChildPointers(Long newExecContextId,
                                               Long newGraphId,
                                               Long newTaskStateId,
                                               Long newVariableStateId) {
        ExecContextImpl ec = execContextRepository.findById(newExecContextId).orElseThrow();
        ec.execContextGraphId = newGraphId;
        ec.execContextTaskStateId = newTaskStateId;
        ec.execContextVariableStateId = newVariableStateId;
        execContextRepository.save(ec);
    }

    /**
     * Commit: flip CLONING -> FINISHED on the new ExecContext.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeCloneState(Long newExecContextId) {
        ExecContextImpl ec = execContextRepository.findById(newExecContextId).orElseThrow();
        ec.state = EnumsApi.ExecContextState.FINISHED.code;
        execContextRepository.save(ec);
    }

    /**
     * MINIMAL ISOLATION DIAGNOSTICS — two independent read paths to the same
     * TaskImpl row, each in its own REQUIRES_NEW TX. Used by
     * {@code test_minimalRepro_preAllocateThenReadInAnotherTx} to determine
     * which (if any) read path can see a row inserted by
     * {@link #preAllocateClonedTask} in a sibling REQUIRES_NEW TX.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TaskImpl debugFindById(Long id) {
        TaskImpl t = taskRepository.findById(id).orElse(null);
        log.info("MINREPRO debugFindById({}) -> {}", id, t == null ? "null" : ("id=" + t.id + " ec=" + t.execContextId));
        return t;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TaskImpl debugFindByIdReadOnly(Long id) {
        TaskImpl t = taskRepository.findByIdReadOnly(id);
        log.info("MINREPRO debugFindByIdReadOnly({}) -> {}", id, t == null ? "null" : ("id=" + t.id + " ec=" + t.execContextId));
        return t;
    }

    /**
     * MINIMAL DIAGNOSTIC HELPER — save a TaskImpl from outside any TX
     * (used by tests that mutate a task's params and need it persisted).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskImpl debugSaveTask(TaskImpl t) {
        return taskRepository.save(t);
    }

    public record TaskClonedIds(Long oldTaskId, Long newTaskId) {}
    public record VariableClonedIds(Long oldVariableId, Long newVariableId) {}
}
