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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public abstract class FeatureMethods extends PreparingPlan {

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

    public boolean isCorrectInit = true;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    public void toStarted() {
        execContextFSM.toStarted(workbook);
        workbook = execContextCache.findById(workbook.getId());
        assertEquals(EnumsApi.ExecContextState.STARTED.code, workbook.getState());
    }

    protected void produceTasks() {
        EnumsApi.SourceCodeValidateStatus status = sourceCodeService.validate(plan);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status);

        SourceCodeApiData.TaskProducingResultComplex result = execContextService.createExecContext(plan.getId(), execContextYaml);
        workbook = (ExecContextImpl)result.execContext;
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.ExecContextState.NONE.code, workbook.getState());


        EnumsApi.SourceCodeProducingStatus producingStatus = execContextService.toProducing(workbook.id);
        workbook = execContextCache.findById(workbook.id);
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, producingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, workbook.getState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks01.isEmpty());

        long mills;

        List<Object[]> tasks02 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = sourceCodeService.produceAllTasks(true, plan, workbook);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        workbook = (ExecContextImpl)result.execContext;
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCED.code, workbook.getState());

        experiment = experimentCache.findById(experiment.getId());
        assertNotNull(experiment.getExecContextId());
    }

    protected DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor_mustBeNewTask() {
        long mills;

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToProcessor()");
        DispatcherCommParamsYaml.AssignedTask task = execContextService.getTaskAndAssignToProcessor(
                processor.getId(), false, experiment.getExecContextId());
        log.info("experimentService.getTaskAndAssignToProcessor() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(task);
        return task;
    }

    protected void finishCurrentWithError(int expectedSeqs) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByProcessorIdAndResultReceivedIsFalse(processor.getId());
        if (expectedSeqs!=0) {
            assertEquals(expectedSeqs, tasks.size());
        }
        for (Task task : tasks) {
            FunctionApiData.SystemExecResult systemExecResult = new FunctionApiData.SystemExecResult("output-of-a-function",false, -1, "This is sample console output");
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(systemExecResult, null, null, null);
            String yaml = FunctionExecUtils.toString(functionExec);

            ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult sser =
                    new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml, null);
            // TODO 2019-11-17 left it here for info. delete when code will be merged in master
            // new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml, MetricsUtils.toString(MetricsUtils.EMPTY_METRICS));
            results.add(sser);
        }

        execContextService.storeAllConsoleResults(results);
    }

    protected void finishCurrentWithOk(int expectedTasks) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByProcessorIdAndResultReceivedIsFalse(processor.getId());
        if (expectedTasks!=0) {
            assertEquals(expectedTasks, tasks.size());
        }
        for (Task task : tasks) {
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.setExec( new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "This is sample console output. fit"));
            String yaml = FunctionExecUtils.toString(functionExec);

            ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult ster =
                    new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml, null);
            results.add(ster);
        }

        execContextService.storeAllConsoleResults(results);
    }


}
