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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
public class TestSimpleSourceCodeWithInternalFunctions extends FeatureMethods {

    @Autowired
    public SouthbridgeService southbridgeService;

    @Autowired
    public TaskService taskService;

    @Autowired
    public ExecContextSchedulerService execContextSchedulerService;

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
        DispatcherCommParamsYaml.AssignedTask t = execContextService.getTaskAndAssignToProcessor(new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId(), false, execContextForTest.getId());
        assertNotNull(t);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        processorComm0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        processorComm0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = southbridgeService.processRequest(processorYaml0, "127.0.0.1");

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);
        assertNotNull(d1);
        assertNull(d1.getAssignedTask());

        finishCurrentWithOk();
        TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task);
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
            Enums.UploadResourceStatus status = taskTransactionalService.setResultReceived(t.taskId, output.id);
            assertEquals(Enums.UploadResourceStatus.OK, status);
        }

        task = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task);
        assertTrue(task.isCompleted);

        execContextFSM.updateExecContextStatus(execContextForTest.id,true);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
    }

    public void step_3(String sessionId) {
        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        processorComm0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        processorComm0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = southbridgeService.processRequest(processorYaml0, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);

        assertNotNull(d);
        assertNotNull(d.getAssignedTask());
        assertNotNull(d.getAssignedTask());
    }

    public void step_4(String sessionId) {
        final ProcessorCommParamsYaml processorComm1 = new ProcessorCommParamsYaml();
        processorComm1.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        processorComm1.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
        String dispatcherResponse1 = southbridgeService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

        assertNotNull(d1);
        assertNull(d1.getAssignedTask());
    }

}



