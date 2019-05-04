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
import aiai.api.v1.launchpad.Process;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.PreparingExperiment;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestCountOfTasks extends PreparingFlow {

    @Override
    public String getFlowParamsAsYaml() {
        return getFlowParamsAsYaml_Simple();
    }

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;

    @Test
    public void testCountNumberOfTasks() {
        log.info("Start TestCountOfTasks.testCountNumberOfTasks()");

        assertFalse(flowYaml.processes.isEmpty());
        assertEquals(EnumsApi.ProcessType.EXPERIMENT, flowYaml.processes.get(flowYaml.processes.size()-1).type);

        EnumsApi.FlowValidateStatus status = flowService.validate(flow);
        assertEquals(EnumsApi.FlowValidateStatus.OK, status);

        FlowService.TaskProducingResult result = flowService.createFlowInstance(flow.getId(), InputResourceParamUtils.toString(inputResourceParam));
        flowInstance = result.flowInstance;
        assertEquals(EnumsApi.FlowProducingStatus.OK, result.flowProducingStatus);
        assertNotNull(flowInstance);
        assertEquals(Enums.FlowInstanceExecState.NONE.code, flowInstance.execState);


        EnumsApi.FlowProducingStatus producingStatus = flowService.toProducing(flowInstance);
        assertEquals(EnumsApi.FlowProducingStatus.OK, producingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCING.code, flowInstance.execState);

        List<Object[]> tasks01 = taskCollector.getTasks(result.flowInstance);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();
        result = flowService.produceAllTasks(false, flow, flowInstance);
        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        assertEquals(EnumsApi.FlowProducingStatus.OK, result.flowProducingStatus);
        int numberOfTasks = result.numberOfTasks;

        List<Object[]> tasks02 = taskCollector.getTasks(result.flowInstance);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = flowService.produceAllTasks(true, flow, flowInstance);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        flowInstance = result.flowInstance;
        assertEquals(EnumsApi.FlowProducingStatus.OK, result.flowProducingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCED.code, flowInstance.execState);

        experiment = experimentCache.findById(experiment.getId());

        List<Object[]> tasks = taskCollector.getTasks(result.flowInstance);

        assertNotNull(result);
        assertNotNull(result.flowInstance);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        result = flowService.produceAllTasks(false, flow, flowInstance);
        List<Object[]> tasks03 = taskCollector.getTasks(flowInstance);
        assertFalse(tasks03.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        int taskNumber = 0;
        for (Process process : flowYaml.processes) {
            if (process.type== EnumsApi.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippetCodes.size();
        }

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  experiment.getNumberOfTask());

    }

}
