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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.server.ServerService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
public class TestTaskRequest extends FeatureMethods {

    @Autowired
    public ServerService serverService;

    @Autowired
    public TaskService taskService;

    @Autowired
    public ExecContextSchedulerService execContextSchedulerService;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();
        String sessionId;
        {
            final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
            processorComm.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, null);
            processorComm.reportProcessorTaskStatus = new ProcessorCommParamsYaml.ReportProcessorTaskStatus(Collections.emptyList());


            final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);
            String dispatcherResponse = serverService.processRequest(processorYaml, "127.0.0.1");

            DispatcherCommParamsYaml d0 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

            assertNotNull(d0);
            assertNotNull(d0.getReAssignedProcessorId());
            assertNotNull(d0.getReAssignedProcessorId().sessionId);
            assertEquals(processorIdAsStr, d0.getReAssignedProcessorId().reAssignedProcessorId);

            sessionId = d0.getReAssignedProcessorId().sessionId;
        }
        List<Object[]> counts = taskRepository.getCountPerOrder(workbook.getId());
        for (Object[] count : counts) {
            if (((Number)count[0]).intValue() > 1) {
                break;
            }

            DispatcherCommParamsYaml.AssignedTask t = execContextService.getTaskAndAssignToProcessor(processor.getId(), false, workbook.getId());
            assertNotNull(t);

            final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
            processorComm0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
            processorComm0.requestTask = new ProcessorCommParamsYaml.RequestTask(false);

            final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
            String dispatcherResponse0 = serverService.processRequest(processorYaml0, "127.0.0.1");

            DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);
            assertNotNull(d1);
            assertNull(d1.getAssignedTask());

            finishCurrentWithOk(1);
            Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(t.taskId, true);
            assertEquals(Enums.UploadResourceStatus.OK, status);

            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            assertNotNull(task);
            assertTrue(task.isCompleted);

            execContextSchedulerService.updateExecContextStatus(workbook.id,true);
            workbook = execContextCache.findById(workbook.id);
        }
        {
            final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
            processorComm0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
            processorComm0.requestTask = new ProcessorCommParamsYaml.RequestTask(false);

            final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
            String dispatcherResponse0 = serverService.processRequest(processorYaml0, Consts.LOCALHOST_IP);

            DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);

            assertNotNull(d);
            assertNotNull(d.getAssignedTask());
            assertNotNull(d.getAssignedTask());
        }
        {
            final ProcessorCommParamsYaml processorComm1 = new ProcessorCommParamsYaml();
            processorComm1.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
            processorComm1.requestTask = new ProcessorCommParamsYaml.RequestTask(false);

            final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
            String dispatcherResponse1 = serverService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

            DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

            assertNotNull(d1);
            assertNull(d1.getAssignedTask());
        }
    }

}