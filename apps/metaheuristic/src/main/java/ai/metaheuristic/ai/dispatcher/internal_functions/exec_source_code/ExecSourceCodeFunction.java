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

package ai.metaheuristic.ai.dispatcher.internal_functions.exec_source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 6/10/2021
 * Time: 3:26 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExecSourceCodeFunction implements InternalFunction {

    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeRepository sourceCodeRepository;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final ExecContextVariableService execContextVariableService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final ExecContextCreatorService execContextCreatorService;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    private final VariableRepository variableRepository;

    @Override
    public String getCode() {
        return Consts.MH_EXEC_SOURCE_CODE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_EXEC_SOURCE_CODE_FUNCTION;
    }

    public boolean isLongRunning() {
        return true;
    }

    @Override
    @SneakyThrows
    public void process(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        String scUid = MetaUtils.getValue(taskParamsYaml.task.metas, Consts.SOURCE_CODE_UID);
        if (S.b(scUid)) {
            throw new InternalFunctionException(meta_not_found, "#508.020 meta '"+ Consts.SOURCE_CODE_UID+"' wasn't found");
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            throw new InternalFunctionException(source_code_not_found, "#508.030 sourceCode #"+simpleExecContext.sourceCodeId+" wasn't found");
        }

        Long subScId = sourceCodeRepository.findIdByUid(scUid);
        if (subScId==null) {
            throw new InternalFunctionException(source_code_not_found, "#508.040 sourceCode '"+scUid+"' wasn't found");
        }
        SourceCodeImpl subSc = sourceCodeCache.findById(subScId);
        if (subSc==null) {
            throw new InternalFunctionException(source_code_not_found, "#508.050 sourceCode #"+subScId+" wasn't found");
        }

        Long rootExecContextId = simpleExecContext.getParamsYaml().execContextGraph!=null
                ? simpleExecContext.getParamsYaml().execContextGraph.rootExecContextId
                : simpleExecContext.execContextId;

        ExecContextData.RootAndParent rootAndParent = new ExecContextData.RootAndParent(rootExecContextId, simpleExecContext.execContextId);

        ExecContextCreatorService.ExecContextCreationResult execContextResultRest =
                execContextCreatorTopLevelService.createExecContextAndStart(
                        subSc.id, simpleExecContext.companyId, false, rootAndParent);

        if (execContextResultRest.isErrorMessages()) {
            throw new InternalFunctionException(exec_context_creation_error,
                            "#508.060 execContext for sourceCode '"+scUid+"' wasn't created, error: " + execContextResultRest.getErrorMessagesAsStr());
        }

        final ExecContextParamsYaml execContextParamsYaml = execContextResultRest.execContext.getExecContextParamsYaml();


        File tempDir=null;
        try {
            tempDir = DirUtils.createMhTempDir("mh-exec-source-code-");
            if (tempDir == null) {
                throw new InternalFunctionException(system_error, "#508.070 can't create a temporary file");
            }
            for (int i = 0; i < taskParamsYaml.task.inputs.size(); i++) {
                TaskParamsYaml.InputVariable input = taskParamsYaml.task.inputs.get(i);
                SimpleVariable sv = variableRepository.findByIdAsSimple(input.id);
                if (sv==null) {
                    throw new InternalFunctionException(variable_not_found, "#508.073 can't find a variable #"+input.id);
                }
                if (sv.nullified) {
                    execContextVariableService.initInputVariableWithNull(execContextResultRest.execContext.id, execContextParamsYaml, i);
                }
                else {
                    File tempFile = File.createTempFile("input-", ".bin", tempDir);
                    switch (input.context) {
                        case global:
                            globalVariableService.storeToFileWithTx(input.id, tempFile);
                            break;
                        case local:
                            variableService.storeToFileWithTx(input.id, tempFile);
                            break;
                        case array:
                            throw new NotImplementedException("Not yet");
                    }
                    try (InputStream is = new FileInputStream(tempFile)) {
                        execContextVariableService.initInputVariable(
                                is, tempFile.length(), "variable-" + input.name, execContextResultRest.execContext.id, execContextParamsYaml, i);
                    }
                }
            }
            for (ExecContextParamsYaml.Variable output : execContextParamsYaml.variables.outputs) {
                execContextVariableService.initOutputVariable(execContextResultRest.execContext.id, output);
            }
        }
        catch (InternalFunctionException e) {
            throw e;
        }
        catch (Throwable th) {
            final String es = "#508.075 error " + th.getMessage();
            log.error(es, th);
            throw new InternalFunctionException(system_error, es);
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }

        execContextCreatorService.produceTasksForExecContext(sourceCode, execContextResultRest);
        if (execContextResultRest.isErrorMessages()) {
            throw new InternalFunctionException(exec_context_starting_error,
                            S.f("#508.077 tasks for execContext #%d, sourceCode '%s' can't be produced, error: %s",
                                    execContextResultRest.execContext.id, scUid, execContextResultRest.getErrorMessagesAsStr()));
        }

        dispatcherParamsTopLevelService.registerLongRunningExecContext(taskId, execContextResultRest.execContext.id);

        OperationStatusRest operationStatusRest = execContextTopLevelService.execContextTargetState(
                execContextResultRest.execContext.id, EnumsApi.ExecContextState.STARTED, simpleExecContext.companyId);

        if (operationStatusRest.isErrorMessages()) {
            throw new InternalFunctionException(exec_context_starting_error,
                            "#508.080 execContext #"+execContextResultRest.execContext.id+" for sourceCode '"+scUid+"' can't be started, error: " + operationStatusRest.getErrorMessagesAsStr());
        }

    }
}
