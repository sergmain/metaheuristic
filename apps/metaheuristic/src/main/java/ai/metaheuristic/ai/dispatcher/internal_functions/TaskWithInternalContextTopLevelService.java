/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 6/19/2021
 * Time: 10:05 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextTopLevelService {

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final TaskWithInternalContextService taskWithInternalContextService;
    private final VariableService variableService;
    private final ExecContextVariableService execContextVariableService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;

    public void storeResult(Long taskId, Long subExecContextId) {
        TxUtils.checkTxNotExists();
        TaskImpl task = taskRepository.findById(taskId).orElseThrow(
                () -> new InternalFunctionException(task_not_found, "#992.020 Task not found #" + taskId));

        final TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        copyVariables(taskParamsYaml, subExecContextId);
        taskWithInternalContextService.storeResult(taskId, taskParamsYaml);
    }

    private void copyVariables(TaskParamsYaml taskParamsYaml, Long subExecContextId) {
        ExecContextImpl execContext = execContextCache.findById(subExecContextId);
        if (execContext == null) {
            throw new InternalFunctionException(exec_context_not_found, "#992.040 ExecContext Not found");
        }
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        if (ecpy.variables.outputs.size() != taskParamsYaml.task.outputs.size()) {
            throw new InternalFunctionException(number_of_outputs_is_incorrect, "#992.060 number_of_outputs_is_incorrect");
        }
        File tempDir = null;
        try {
            tempDir = DirUtils.createMhTempDir("mh-exec-source-code-result-");
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
                                    "#992.120 local variable with name: " + execContextOutput.name + " wasn't found");
                }

                File tempFile = File.createTempFile("output-", ".bin", tempDir);

                variableService.storeToFileWithTx(variableHolder.variable.id, tempFile);
                execContextVariableService.storeDataInVariable(output, tempFile);
            }
        } catch (IOException e) {
            log.error("#992.220 Error", e);
            throw new InternalFunctionException(system_error, "#992.240 error: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (Throwable th) {
                    log.error("#992.260 Error", th);
                }
            }
        }
    }
}
