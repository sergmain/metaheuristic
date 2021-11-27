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
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskVariableTopLevelService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 3/14/2020
 * Time: 8:53 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestSimpleSourceCodeWithInternalFunctions extends FeatureMethods {

    @Autowired
    public SouthbridgeService southbridgeService;

    @Autowired
    public TaskService taskService;

    @Autowired
    public ExecContextSchedulerService execContextSchedulerService;

    @Autowired
    private TaskVariableTopLevelService taskVariableTopLevelService;

    @Override
    @SneakyThrows
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/simple-internal-functions-v1.yaml", StandardCharsets.UTF_8);
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        // Function mh.finish will be created even not defined explicitly
        assertEquals(3, countTasks(null));
        toStarted();
        String sessionId = initSessionId();

//        step_2(sessionId);
//        step_3(sessionId);
//        step_4(sessionId);
    }

    public void step_2(String sessionId) {
        final DispatcherCommParamsYaml.AssignedTask t = taskProviderService.findTask(processor.getId(), false);
        assertNotNull(t);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm0.requests.add(req0);

        req0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = southbridgeService.processRequest(processorYaml0, "127.0.0.1");

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);
        assertNotNull(d1);
        assertNotNull(d1.responses);
        assertEquals(1, d1.responses.size());
        assertNull(d1.responses.get(0).getAssignedTask());

        storeConsoleResultAsOk();
        TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task);
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
            Enums.UploadVariableStatus status = TaskSyncService.getWithSyncNullable(t.taskId,
                    () -> taskVariableTopLevelService.updateStatusOfVariable(t.taskId, output.id).status);
            assertEquals(Enums.UploadVariableStatus.OK, status);
        }

        task = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task);
        assertTrue(task.isCompleted);

        execContextTopLevelService.updateExecContextStatus(execContextForTest.id);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
    }

    public void step_3(String sessionId) {
        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm0.requests.add(req0);

        req0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = southbridgeService.processRequest(processorYaml0, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);

        assertNotNull(d);
        assertNotNull(d.responses);
        assertEquals(1, d.responses.size());
        final DispatcherCommParamsYaml.AssignedTask assignedTask = d.responses.get(0).getAssignedTask();
        assertNotNull(assignedTask);
        assertNotNull(assignedTask.taskId);
    }

    public void step_4(String sessionId) {
        final ProcessorCommParamsYaml processorComm1 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req1 = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm1.requests.add(req1);

        req1.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req1.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);

        final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
        String dispatcherResponse1 = southbridgeService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

        assertNotNull(d1);
        assertNotNull(d1.responses);
        assertEquals(1, d1.responses.size());
        final DispatcherCommParamsYaml.AssignedTask assignedTask = d1.responses.get(0).getAssignedTask();
        assertNotNull(assignedTask);
    }

}



