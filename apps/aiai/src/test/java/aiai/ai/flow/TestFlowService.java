/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.flow;

import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.api.v1.launchpad.Process;
import aiai.api.v1.launchpad.Task;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.api.v1.EnumsApi;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestFlowService extends PreparingFlow {

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public TaskCollector taskCollector;


    @SuppressWarnings("Duplicates")
    @Override
    public String getFlowParamsAsYaml() {
        return getFlowParamsAsYaml_Simple();
    }

    @After
    public void afterTestFlowService() {
        if (flowInstance!=null) {
            try {
                taskRepository.deleteByFlowInstanceId(flowInstance.getId());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Finished TestFlowService.afterTestFlowService()");
    }

    @Test
    public void testCreateTasks() {
        assertFalse(flowYaml.processes.isEmpty());
        assertEquals(EnumsApi.ProcessType.EXPERIMENT, flowYaml.processes.get(flowYaml.processes.size()-1).type);

        Enums.FlowValidateStatus status = flowService.validate(flow);
        assertEquals(Enums.FlowValidateStatus.OK, status);

        FlowService.TaskProducingResult result = flowService.createFlowInstance(flow, InputResourceParamUtils.toString(inputResourceParam));
        flowInstance = result.flowInstance;

        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        assertNotNull(flowInstance);
        assertEquals(Enums.FlowInstanceExecState.NONE.code, flowInstance.execState);


        Enums.FlowProducingStatus producingStatus = flowService.toProducing(flowInstance);
        assertEquals(Enums.FlowProducingStatus.OK, producingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCING.code, flowInstance.execState);

        result = flowService.produceAllTasks(true, flow, flowInstance);
        flowInstance = result.flowInstance;
        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCED.code, flowInstance.execState);

        experiment = experimentCache.findById(experiment.getId());

        List<Object[]> tasks = taskCollector.getTasks(flowInstance);

        assertNotNull(result);
        assertNotNull(result.flowInstance);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        int taskNumber = 0;
        for (Process process : flowYaml.processes) {
            if (process.type== EnumsApi.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippetCodes.size();
        }

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  experiment.getNumberOfTask());

        // ======================

        TaskService.TasksAndAssignToStationResult assignToStation0 =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());

        Protocol.AssignedTask.Task simpleTask0 = assignToStation0.getSimpleTask();
        assertNull(simpleTask0);

        flowInstance = flowService.toStarted(flowInstance);

        TaskService.TasksAndAssignToStationResult assignToStation =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());

        Protocol.AssignedTask.Task simpleTask = assignToStation.getSimpleTask();
        assertNotNull(simpleTask);
        assertNotNull(simpleTask.getTaskId());
        Task task = taskRepository.findById(simpleTask.getTaskId()). orElse(null);
        assertNotNull(task);
        assertEquals(1, task.getOrder());

        TaskService.TasksAndAssignToStationResult assignToStation2 =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());
        assertNull(assignToStation2.getSimpleTask());

        SimpleTaskExecResult r = new SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setMetrics(null);
        r.setResult("Everything is Ok.");
        r.setResult(getExecResult(true));
        taskPersistencer.storeExecResult(r);
        flowService.markOrderAsProcessed();

        TaskService.TasksAndAssignToStationResult assignToStation3 =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());

        Protocol.AssignedTask.Task simpleTask3 = assignToStation3.getSimpleTask();
        assertNotNull(simpleTask3);
        assertNotNull(simpleTask3.getTaskId());
        Task task3 = taskRepository.findById(simpleTask3.getTaskId()). orElse(null);
        assertNotNull(task3);
        assertEquals(2, task3.getOrder());

        TaskService.TasksAndAssignToStationResult assignToStation4 =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());
        assertNull(assignToStation4.getSimpleTask());

        flowService.markOrderAsProcessed();

        TaskService.TasksAndAssignToStationResult assignToStation51 =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());

        Protocol.AssignedTask.Task simpleTask51 = assignToStation51.getSimpleTask();
        assertNotNull(simpleTask51);
        assertNotNull(simpleTask51.getTaskId());
        Task task51 = taskRepository.findById(simpleTask51.getTaskId()). orElse(null);
        assertNotNull(task51);
        assertEquals(3, task51.getOrder());

        TaskService.TasksAndAssignToStationResult assignToStation52 =
                taskService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());

        Protocol.AssignedTask.Task simpleTask52 = assignToStation52.getSimpleTask();
        assertNotNull(simpleTask52);
        assertNotNull(simpleTask52.getTaskId());
        Task task52 = taskRepository.findById(simpleTask52.getTaskId()).orElse(null);
        assertNotNull(task52);
        assertEquals(3, task52.getOrder());

        int i=0;
    }

    private String getExecResult(boolean isOk) {
        SnippetExec snippetExec = new SnippetExec();
        snippetExec.setExec( new ExecProcessService.Result(true, 0, null) );

        return SnippetExecUtils.toString(snippetExec);
    }
}
