/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public abstract class FeatureMethods extends PreparingSourceCode {

    @Autowired
    protected Globals globals;

    @Autowired
    protected ExperimentService experimentService;

    @Autowired
    protected ExperimentRepository experimentRepository;

    @Autowired
    protected FunctionCache functionCache;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected TaskService taskService;

    @Autowired
    public ExecContextService execContextService;

    @Autowired
    public ExecContextFSM execContextFSM;

    @Autowired
    public ExecContextCreatorService execContextCreatorService;

    @Autowired
    public SouthbridgeService southbridgeService;

    public boolean isCorrectInit = true;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    public long countTasks(@Nullable List<EnumsApi.ExecContextState> states) {
        List<Object[]> list = taskRepository.findAllExecStateByExecContextId(execContextForTest.id);
        if (states==null) {
            return list.size();
        }
        long count = list.stream().filter(o->states.contains(EnumsApi.ExecContextState.toState((Integer)o[1]))).count();
        return count;
    }

    public void toStarted() {
        execContextSyncService.getWithSync(execContextForTest.id, () -> {
            execContextFSM.toStarted(execContextForTest);
            execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.getId()));
            assertEquals(EnumsApi.ExecContextState.STARTED.code, execContextForTest.getState());
            return null;
        });
    }

    public String initSessionId() {
        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        processorComm.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, null);
        processorComm.reportProcessorTaskStatus = new ProcessorCommParamsYaml.ReportProcessorTaskStatus(Collections.emptyList());


        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = southbridgeService.processRequest(processorYaml, "127.0.0.1");

        DispatcherCommParamsYaml d0 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d0);
        assertNotNull(d0.getReAssignedProcessorId());
        assertNotNull(d0.getReAssignedProcessorId().sessionId);
        assertEquals(processorIdAsStr, d0.getReAssignedProcessorId().reAssignedProcessorId);

        String sessionId = d0.getReAssignedProcessorId().sessionId;
        return sessionId;
    }

    protected void produceTasks() {
        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;
        assertFalse(result.isErrorMessages());
        assertNotNull(execContextForTest);
        assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForTest.getState());
        execContextSyncService.getWithSync(execContextForTest.id, () -> {
            EnumsApi.TaskProducingStatus producingStatus = execContextFSM.toProducing(execContextForTest.id, execContextService);
            execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
            assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);
            assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForTest.getState());

            List<Object[]> tasks01 = taskCollector.getTasks(result.execContext);
            assertTrue(tasks01.isEmpty());

            List<Object[]> tasks02 = taskCollector.getTasks(result.execContext);
            assertTrue(tasks02.isEmpty());

            long mills = System.currentTimeMillis();
            SourceCodeApiData.TaskProducingResultComplex taskProducingResultComplex = sourceCodeService.produceAllTasks(true, sourceCode, execContextForTest);
            log.info("All tasks were produced for " + (System.currentTimeMillis() - mills) + " ms.");

            execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
            assertEquals(EnumsApi.TaskProducingStatus.OK, taskProducingResultComplex.taskProducingStatus);
            assertEquals(EnumsApi.ExecContextState.PRODUCED, EnumsApi.ExecContextState.toState(execContextForTest.getState()));

            return null;
        });
    }

    protected DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor_mustBeNewTask() {
        long mills;

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToProcessor()");
        DispatcherCommParamsYaml.AssignedTask task = execContextService.getTaskAndAssignToProcessor(
                new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId(), false, execContextForTest.id);
        log.info("experimentService.getTaskAndAssignToProcessor() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(task);
        return task;
    }

    protected void finishCurrentWithError() {
        // lets report about tasks that all finished with an error (errorCode!=0)
        List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByProcessorIdAndResultReceivedIsFalse(processor.getId());
        assertEquals(1, tasks.size());
        for (Task task : tasks) {
            FunctionApiData.SystemExecResult systemExecResult = new FunctionApiData.SystemExecResult("output-of-a-function",false, -1, "This is sample console output");
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(systemExecResult, null, null, null);
            String yaml = FunctionExecUtils.toString(functionExec);

            ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult sser =
                    new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml);
            results.add(sser);
        }

        execContextService.storeAllConsoleResults(results);
    }

    protected void finishCurrentWithOk() {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByProcessorIdAndResultReceivedIsFalse(processor.getId());
        assertEquals(1, tasks.size());
        for (Task task : tasks) {
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.setExec( new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "This is sample console output. fit"));
            String yaml = FunctionExecUtils.toString(functionExec);

            ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult ster =
                    new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml);
            results.add(ster);
        }

        execContextService.storeAllConsoleResults(results);
    }


}
