/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("WeakerAccess")
@Slf4j
public abstract class FeatureMethods extends PreparingExperiment {

    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private TaskProviderTopLevelService taskProviderService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private SourceCodeValidationService sourceCodeValidationService;
    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    protected DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor_mustBeNewTask(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        long mills;

        mills = System.currentTimeMillis();

        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        // get a task for processing
        log.info("Start experimentService.getTaskAndAssignToProcessor()");
        DispatcherCommParamsYaml.AssignedTask task = taskProviderService.findTask(processorIdAndCoreIds.coreId1, false);
        log.info("experimentService.getTaskAndAssignToProcessor() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        assertNotNull(task);
        return task;
    }

    protected void produceTasks() {
        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(getSourceCode());
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertFalse(result.isErrorMessages());
        assertNotNull(getExecContextForTest());
        assertEquals(EnumsApi.ExecContextState.NONE.code, getExecContextForTest().getState());
        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () -> {
            EnumsApi.TaskProducingStatus producingStatus = txSupportForTestingService.toProducing(getExecContextForTest().id);
            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
            assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);
            assertEquals(EnumsApi.ExecContextState.PRODUCING.code, getExecContextForTest().getState());

            List<Object[]> tasks01 = taskRepositoryForTest.findByExecContextId(result.execContext.id);
            assertTrue(tasks01.isEmpty());

            List<Object[]> tasks02 = taskRepositoryForTest.findByExecContextId(result.execContext.id);
            assertTrue(tasks02.isEmpty());

            long mills = System.currentTimeMillis();
            ExecContextParamsYaml execContextParamsYaml = result.execContext.getExecContextParamsYaml();
            ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, ()-> {
                        txSupportForTestingService.produceAndStartAllTasks(getSourceCode(), result.execContext.id);
                    }));

            log.info("Tasks were produced for " + (System.currentTimeMillis() - mills) + " ms.");
        });
        // statuses of ExecContext are being refreshing from Scheduler which is disabled while testing
        execContextStatusService.resetStatus();
        assertEquals(EnumsApi.ExecContextState.STARTED, execContextStatusService.getExecContextStatuses().statuses.get(getExecContextForTest().id));

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
        assertEquals(EnumsApi.ExecContextState.STARTED, EnumsApi.ExecContextState.toState(getExecContextForTest().getState()));
    }

    protected void storeConsoleResultAsError(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        // lets report about tasks that all finished with an error (errorCode!=0)
        List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<TaskImpl> tasks = taskRepositoryForTest.findByCoreIdAndResultReceivedIs0(processorIdAndCoreIds.coreId1);
        assertEquals(1, tasks.size());

        TaskImpl task = tasks.get(0);
        FunctionApiData.SystemExecResult systemExecResult = new FunctionApiData.SystemExecResult("output-of-a-function",false, -1, "This is sample console output");
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(systemExecResult, null, null, null);
        String yaml = FunctionExecUtils.toString(functionExec);

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult sser =
                new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml);
        results.add(sser);

        execContextTopLevelService.storeAllConsoleResults(results);
    }

    protected void storeConsoleResultAsOk(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<TaskImpl> tasks = taskRepositoryForTest.findByCoreIdAndResultReceivedIs0(processorIdAndCoreIds.coreId1);
        assertEquals(1, tasks.size());

        TaskImpl task = tasks.get(0);
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
        functionExec.setExec( new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "This is sample console output. fit"));
        String yaml = FunctionExecUtils.toString(functionExec);

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult ster =
                new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml);
        results.add(ster);

        execContextTopLevelService.storeAllConsoleResults(results);
    }


}
