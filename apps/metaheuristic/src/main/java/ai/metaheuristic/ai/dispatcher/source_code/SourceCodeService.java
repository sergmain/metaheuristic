/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeValidationService sourceCodeValidationService;

    private final ExecContextFSM execContextFSM;
    private final TaskProducingService taskProducingService;

    @Transactional
    public OperationStatusRest deleteSourceCodeById(@Nullable Long sourceCodeId) {
        if (sourceCodeId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.240 Can't delete a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCode sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.250 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        sourceCodeCache.deleteById(sourceCodeId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    // TODO 2020.09.28 reporting is about dynamically inform a web application about the current status of creating
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<ExecContextImpl> execContexts = execContextRepository.findByState(EnumsApi.ExecContextState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!execContexts.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl execContext : execContexts) {
            SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
            if (sourceCode==null) {
                execContextFSM.toStopped(execContext.id);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}",sourceCode.uid, execContext.getParams());
            produceAllTasks(true, sourceCode, execContext);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!execContexts.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }

    public SourceCodeApiData.TaskProducingResultComplex produceAllTasks(boolean isPersist, SourceCodeImpl sourceCode, ExecContextImpl execContext) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist && execContext.getState()!= EnumsApi.ExecContextState.PRODUCING.code) {
            result.sourceCodeValidationResult = new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.ALREADY_PRODUCED_ERROR, "Tasks were produced already");
            return result;
        }
        long mills = System.currentTimeMillis();
        result.sourceCodeValidationResult = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        log.info("#701.150 SourceCode was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.OK &&
                result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.160 Can't produce tasks, error: {}", result.sourceCodeValidationResult);
            if(isPersist) {
                execContextFSM.toStopped(execContext.getId());
            }
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = taskProducingService.produceTasks(isPersist, execContext);
        log.info("#701.170 SourceCodeService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    public static List<SourceCodeParamsYaml.Variable> findVariableByType(SourceCodeParamsYaml scpy, String type) {
        List<SourceCodeParamsYaml.Variable> list = new ArrayList<>();
        for (SourceCodeParamsYaml.Process process : scpy.source.processes) {
            findVariableByType(process, type, list);
        }
        return list;

    }

    private static void findVariableByType(SourceCodeParamsYaml.Process process, String type, List<SourceCodeParamsYaml.Variable> list) {
        for (SourceCodeParamsYaml.Variable output : process.outputs) {
            if (output.type.equals(type)) {
                list.add(output);
            }
        }
        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process p : process.subProcesses.processes) {
                findVariableByType(p, type, list);
            }
        }
    }

}
