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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestTaskRequest extends FeatureMethods {

    @Autowired private TaskFinishingTopLevelService taskFinishingTopLevelService;
    @Autowired private TaskVariableTopLevelService taskVariableTopLevelService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextTaskStateTopLevelService execContextTaskStateTopLevelService;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private SouthbridgeService serverService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextService execContextService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();
        String sessionId = preparingSourceCodeService.step_1_0_init_session_id(getProcessorIdAsStr());
        preparingSourceCodeService.step_1_1_register_function_statuses(sessionId, getProcessorIdAsStr(), preparingSourceCodeData, preparingCodeData);
        step_2(sessionId);
        step_3(sessionId);
        step_4(sessionId);
    }

    private void step_2(String sessionId) {
        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        // get a task for processing
        DispatcherCommParamsYaml.AssignedTask t = taskProviderTopLevelService.findTask(getProcessor().getId(), false);
        assertNotNull(t);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm0.requests.add(req0);

        req0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(getProcessorIdAsStr(), sessionId);
        req0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);

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
                Enums.UploadVariableStatus status = taskVariableTopLevelService.updateStatusOfVariable(task.id, output.id).status;
                assertEquals(Enums.UploadVariableStatus.OK, status);
            }
            return null;
        });
        taskFinishingTopLevelService.checkTaskCanBeFinished(task.id);
        TaskQueue.TaskGroup taskGroup =
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        getExecContextForTest().id, getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)));

        final TaskImpl task2 = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task2);
        assertTrue(task2.isCompleted);

        execContextTopLevelService.updateExecContextStatus(getExecContextForTest().id);
        setExecContextForTest(Objects.requireNonNull(execContextService.findById(getExecContextForTest().id)));
    }

    private void step_3(String sessionId) {
        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm0.requests.add(req0);
        req0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(getProcessorIdAsStr(), sessionId);
        req0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);

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
        req1.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(getProcessorIdAsStr(), sessionId);
        req1.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);

        final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
        String dispatcherResponse1 = serverService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

        assertNotNull(d1);
        assertEquals(1, d1.responses.size());
        assertNull(d1.responses.get(0).getAssignedTask());
    }

}
