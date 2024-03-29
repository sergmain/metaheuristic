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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.events.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 6/19/2021
 * Time: 10:05 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskWithInternalContextTopLevelService {

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final TaskWithInternalContextService taskWithInternalContextService;
    private final VariableTxService variableTxService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;

    public void storeResult(Long taskId, Long subExecContextId) {
        TxUtils.checkTxNotExists();
        TaskImpl task = taskRepository.findByIdReadOnly(taskId);

        if (task==null) {
            throw new InternalFunctionException(task_not_found, "#992.020 Task not found #" + taskId);
        }

        TaskParamsYaml taskParamsYaml = task.getTaskParamsYaml();

        copyVariables(subExecContextId, task, taskParamsYaml);
        taskWithInternalContextService.storeResult(taskId, taskParamsYaml.task.function.code);
    }

    private void copyVariables(Long subExecContextId, TaskImpl task, TaskParamsYaml taskParamsYaml) {
        ExecContextImpl subExecContext = execContextCache.findById(subExecContextId, true);
        if (subExecContext == null) {
            throw new InternalFunctionException(exec_context_not_found, "#992.040 ExecContext Not found #" +subExecContextId);
        }
        ExecContextParamsYaml ecpy = subExecContext.getExecContextParamsYaml();
        if (ecpy.variables.outputs.size() != taskParamsYaml.task.outputs.size()) {
            throw new InternalFunctionException(number_of_outputs_is_incorrect, "#992.060 number_of_outputs_is_incorrect");
        }
        ExecContextImpl ec = execContextCache.findById(task.execContextId, true);
        if (ec == null) {
            throw new InternalFunctionException(exec_context_not_found, "#992.045 ExecContext Not found, #"+task.execContextId);
        }
        Path tempDir = null;
        try {
            tempDir = DirUtils.createMhTempPath("mh-exec-source-code-result-");
            if (tempDir == null) {
                throw new InternalFunctionException(system_error,
                                "#992.100 Can't create temporary directory in dir " + SystemUtils.JAVA_IO_TMPDIR);
            }

            for (int i = 0; i < taskParamsYaml.task.outputs.size(); i++) {
                TaskParamsYaml.OutputVariable output = taskParamsYaml.task.outputs.get(i);
                ExecContextParamsYaml.Variable execContextOutput = ecpy.variables.outputs.get(i);

                List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(
                        subExecContextId, Consts.TOP_LEVEL_CONTEXT_ID, execContextOutput.name);
                if (holders.size() > 1) {
                    throw new InternalFunctionException(source_code_is_broken,
                                    "#992.110 Too many variables with the same name at top-level context, name: " + execContextOutput.name);
                }

                VariableUtils.VariableHolder variableHolder = holders.get(0);
                if (variableHolder.variable == null) {
                    throw new InternalFunctionException(variable_not_found,
                                    "#992.120 only local variable is supported right now. variable with name: " + execContextOutput.name + " wasn't found in local context");
                }
                if (!variableHolder.variable.inited) {
                    throw new InternalFunctionException(variable_not_found,
                            "#992.120 local variable name " + execContextOutput.name + " wasn't inited, variableId: #"+variableHolder.variable.id);
                }
                if (variableHolder.variable.nullified) {
                    VariableSyncService.getWithSyncVoidForCreation(output.id,
                            ()->variableTxService.setVariableAsNull(task.id, output.id));
                }
                else {
                    Path tempFile = Files.createTempFile(tempDir, "output-", CommonConsts.BIN_EXT);
                    variableTxService.storeToFileWithTx(variableHolder.variable.id, tempFile);
                    VariableSyncService.getWithSyncVoidForCreation(output.id,
                            ()-> variableTxService.storeDataInVariable(output, tempFile));
                }
                // we don't need to use 'flushing' of state because this method is for copying variable from long-running process
                VariableUploadedEvent event = new VariableUploadedEvent(ec.id, task.id, output.id, variableHolder.variable.nullified);
                execContextVariableStateTopLevelService.registerVariableStateInternal(event.execContextId, List.of(event), ec.execContextVariableStateId);
            }
        }
        catch (IOException e) {
            log.error("#992.220 Error", e);
            throw new InternalFunctionException(system_error, "#992.240 error: " + e);
        }
        finally {
            DirUtils.deletePathAsync(tempDir);
        }
    }
}
