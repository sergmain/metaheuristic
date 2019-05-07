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

package aiai.ai.plan;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.TaskImpl;
import aiai.ai.launchpad.server.ServerService;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.FeatureMethods;
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
@ActiveProfiles("launchpad")
public class TestTaskRequest extends FeatureMethods {

    @Autowired
    public ServerService serverService;

    @Autowired
    public TaskService taskService;

    @Override
    public String getPlanParamsAsYaml() {
        return getPlanParamsAsYaml_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();

        List<Object[]> counts = taskRepository.getCountPerOrder(workbook.getId());
        for (Object[] count : counts) {
            if (((Number)count[0]).intValue() > 1) {
                break;
            }
            ExchangeData data = new ExchangeData();
            data.setStationId(stationIdAsStr);
            data.setCommand(new Protocol.RequestTask(false));

//            ExchangeData d = serverService.processRequest(data, Consts.LOCALHOST_IP);
            Protocol.AssignedTask r = new Protocol.AssignedTask();
            TaskService.TasksAndAssignToStationResult result = taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
            if (result.getSimpleTask()!=null) {
                r.tasks = Collections.singletonList(result.getSimpleTask());
            }
            ExchangeData d = new ExchangeData();
            d.setAssignedTask(r);

            assertNotNull(d);
            assertNotNull(d.getAssignedTask());
            assertNotNull(d.getAssignedTask().tasks);
            assertFalse(d.getAssignedTask().tasks.isEmpty());
            Protocol.AssignedTask.Task t = d.getAssignedTask().tasks.get(0);

            ExchangeData data1 = new ExchangeData();
            data1.setStationId(stationIdAsStr);
            data1.setCommand(new Protocol.RequestTask(false));

            ExchangeData d1 = serverService.processRequest(data1, Consts.LOCALHOST_IP);
            assertNotNull(d1);
            assertNotNull(d1.getAssignedTask());
            assertNull(d1.getAssignedTask().tasks);

            finishCurrentWithOk(1);
            Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(t.taskId, true);
            assertEquals(Enums.UploadResourceStatus.OK, status);

            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            assertNotNull(task);
            assertTrue(task.isCompleted);

            int order = workbook.producingOrder;
            workbook = planService.markOrderAsProcessed(workbook);
            assertEquals(order + 1, workbook.producingOrder);
        }

        ExchangeData data = new ExchangeData();
        data.setStationId(stationIdAsStr);
        data.setCommand(new Protocol.RequestTask(false));

        ExchangeData d = serverService.processRequest(data, Consts.LOCALHOST_IP);
        assertNotNull(d);
        assertNotNull(d.getAssignedTask());
        assertNotNull(d.getAssignedTask().tasks);
        assertFalse(d.getAssignedTask().tasks.isEmpty());

        ExchangeData data1 = new ExchangeData();
        data1.setStationId(stationIdAsStr);
        data1.setCommand(new Protocol.RequestTask(false));

        ExchangeData d1 = serverService.processRequest(data1, Consts.LOCALHOST_IP);
        assertNotNull(d1);
        assertNotNull(d1.getAssignedTask());
        assertNull(d1.getAssignedTask().tasks);

    }

}
