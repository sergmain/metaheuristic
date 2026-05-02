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
        TaskImpl src = taskRepository.findByIdReadOnly(sourceTaskId);
        if (src == null) {
            throw new IllegalStateException("Source task not found: " + sourceTaskId);
        }
        TaskImpl dst = new TaskImpl();
        dst.coreId = null;
        dst.assignedOn = null;
        dst.completedOn = null;
        dst.completed = 0;
        dst.functionExecResults = null;
        dst.execContextId = newExecContextId;
        dst.execState = EnumsApi.TaskExecState.NONE.value;
        dst.resultReceived = 0;
        dst.resultResourceScheduledOn = 0L;
        dst.accessByProcessorOn = null;
        dst.setParams(src.getParams());
        TaskImpl saved = taskRepository.save(dst);
        return new TaskClonedIds(sourceTaskId, saved.id);
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
        dst.inited = src.inited;
        dst.nullified = src.nullified;
        dst.variableBlobId = src.variableBlobId;  // blob is shared, not copied
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

    public record TaskClonedIds(Long oldTaskId, Long newTaskId) {}
    public record VariableClonedIds(Long oldVariableId, Long newVariableId) {}
}
