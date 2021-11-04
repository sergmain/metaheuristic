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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestTaskRequest extends FeatureMethods {

    @Autowired
    public TaskService taskService;

    @Autowired
    public ExecContextSchedulerService execContextSchedulerService;

    @Autowired
    public TaskFinishingTopLevelService taskFinishingTopLevelService;

    @Autowired
    private TaskSyncService taskSyncService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();
        String sessionId = step_1_0_init_session_id();
        step_1_1_register_function_statuses(sessionId);
        step_2(sessionId);
        step_3(sessionId);
        step_4(sessionId);
    }

    private void step_2(String sessionId) {
        findTaskForRegisteringInQueueAndWait(execContextForTest.id);

        // get a task for processing
        DispatcherCommParamsYaml.AssignedTask t = taskProviderService.findTask(processor.getId(), false);
        assertNotNull(t);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm0.requests.add(req0);

        req0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = serverService.processRequest(processorYaml0, "127.0.0.1");

        // get a task for processing one more time
        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);
        assertNotNull(d1);
        // there isn't a new task for processing
        // we will get the same task
        assertEquals(1, d1.responses.size());
        final DispatcherCommParamsYaml.DispatcherResponse response = d1.responses.get(0);
        assertNotNull(response);
        assertNotNull(response.getAssignedTask());
        assertEquals(t.taskId, response.getAssignedTask().taskId);

        storeConsoleResultAsOk();
        final TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task);

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        TaskSyncService.getWithSyncNullable(task.id, () -> {
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Enums.UploadVariableStatus status = txSupportForTestingService.setVariableReceivedWithTx(task.id, output.id);
                assertEquals(Enums.UploadVariableStatus.OK, status);
            }
            return null;
        });
        taskFinishingTopLevelService.checkTaskCanBeFinished(task.id);
        TaskQueue.TaskGroup taskGroup =
                execContextGraphSyncService.getWithSync(execContextForTest.execContextGraphId, ()->
                        execContextTaskStateSyncService.getWithSync(execContextForTest.execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        execContextForTest.id, execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId)));

        final TaskImpl task2 = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task2);
        assertTrue(task2.isCompleted);

        execContextTopLevelService.updateExecContextStatus(execContextForTest.id);
        execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));
    }

    private void step_3(String sessionId) {
        findTaskForRegisteringInQueueAndWait(execContextForTest.id);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm0.requests.add(req0);
        req0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = serverService.processRequest(processorYaml0, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);

        assertNotNull(d);
        assertEquals(1, d.responses.size());
        assertNotNull(d.responses.get(0).getAssignedTask());
    }

    private void step_4(String sessionId) {
        final ProcessorCommParamsYaml processorComm1 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req1 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm1.requests.add(req1);
        req1.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req1.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
        String dispatcherResponse1 = serverService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

        assertNotNull(d1);
        assertEquals(1, d1.responses.size());
        assertNull(d1.responses.get(0).getAssignedTask());
    }

}
