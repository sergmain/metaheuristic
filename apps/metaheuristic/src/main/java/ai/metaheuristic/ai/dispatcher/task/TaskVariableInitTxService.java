/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UpdateTaskExecStatesInExecContextTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 6/10/2023
 * Time: 9:08 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskVariableInitTxService {

    private final VariableTxService variableTxService;
    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextCache execContextCache;
    private final TaskTxService taskTxService;
    private final TaskExecStateService taskExecStateService;
    private final TaskRepository taskRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional(rollbackFor = CommonRollbackException.class)
    public void intiVariables(InitVariablesEvent event) {
        TaskImpl task = taskRepository.findById(event.taskId).orElse(null);
        if (task==null) {
            throw new CommonRollbackException();
        }
        if (task.execState!=EnumsApi.TaskExecState.INIT.value) {
            throw new CommonRollbackException();
        }
        final TaskParamsYaml paramsYaml = task.getTaskParamsYaml();
        if (paramsYaml.task.init==null) {
            throw new CommonRollbackException();
        }

        ExecContextImpl ec = execContextCache.findById(task.execContextId, true);
        if (ec==null) {
            throw new CommonRollbackException();
        }

        List<String> allParentTaskContextIds = getAllParentTaskContextIds(task, paramsYaml.task.init.parentTaskIds, paramsYaml.task.taskContextId, ec);
        if (allParentTaskContextIds!=null) {
            prepareVariables(ec.getExecContextParamsYaml(), task, allParentTaskContextIds);
        }
        task.execState = paramsYaml.task.init.nextState.value;

        taskRepository.save(task);
        taskExecStateService.updateTaskExecStates(task, paramsYaml.task.init.nextState, false);
        eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent(new UpdateTaskExecStatesInExecContextTxEvent(task.execContextId, List.of(task.id)));
    }

    private void prepareVariables(ExecContextParamsYaml execContextParamsYaml, TaskImpl task, List<String> allParentTaskContextIds) {
        TxUtils.checkTxExists();

        TaskParamsYaml taskParams = task.getTaskParamsYaml();

        final Long execContextId = task.execContextId;
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("179.040 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return;
        }

        p.inputs.stream()
                .map(v -> toInputVariable(allParentTaskContextIds, v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        variableTxService.initOutputVariables(execContextId, task, p, taskParams);
    }

    private TaskParamsYaml.InputVariable toInputVariable(List<String> allParentTaskContextIds, ExecContextParamsYaml.Variable v, String taskContextId, Long execContextId) {
        TaskParamsYaml.InputVariable iv = new TaskParamsYaml.InputVariable();
        if (v.context==EnumsApi.VariableContext.local || v.context==EnumsApi.VariableContext.array) {
            String contextId = Boolean.TRUE.equals(v.parentContext) ? VariableUtils.getParentContext(taskContextId) : taskContextId;
            if (S.b(contextId)) {
                throw new TaskCreationException(
                        S.f("179.080 (S.b(contextId)), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            Object[] variable = variableTxService.findVariableInAllInternalContexts(allParentTaskContextIds, v.name, execContextId);
            if (variable==null) {
                throw new TaskCreationException(
                        S.f("179.120 (variable==null), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            iv.id = (Long)variable[0];
            iv.filename = (String)variable[1];
        }
        else {
            SimpleGlobalVariable variable = globalVariableRepository.findIdByName(v.name);
            if (variable==null) {
                throw new TaskCreationException(
                        S.f("179.160 (variable==null), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            iv.id = variable.id;
        }
        iv.context = v.context;
        iv.name = v.name;
        iv.sourcing = v.sourcing;
        iv.disk = v.disk;
        iv.git = v.git;
        iv.type = v.type;
        iv.setNullable(v.getNullable());
        return iv;
    }

    @Nullable
    private List<String> getAllParentTaskContextIds(TaskImpl task, List<Long> parentTaskIds, String taskContextId, ExecContextImpl ec) {
        ExecContextGraph ecg = execContextGraphCache.findById(ec.execContextGraphId);
        if (ecg==null) {
            log.error("179.200 can't find ExecContextGraph #" + ec.execContextGraphId);
            return null;
        }
        Set<String> set = new HashSet<>();
        for (Long parentTaskId : parentTaskIds) {
            ExecContextData.TaskVertex vertex = ExecContextGraphService.findVertexByTaskId(ecg, parentTaskId);
            if (vertex==null) {
                throw new RuntimeException("179.240 vertex wasn't found for task #" + task.id);
            }
            set.add(vertex.taskContextId);
            Set<ExecContextData.TaskVertex> setTemp = ExecContextGraphService.findAncestors(ecg, vertex);
            setTemp.stream().map(o->o.taskContextId).collect(Collectors.toCollection(()->set));
        }
        set.add(taskContextId);

        List<String> list = ContextUtils.sortSetAsTaskContextId(set);
        return list;
    }
}
