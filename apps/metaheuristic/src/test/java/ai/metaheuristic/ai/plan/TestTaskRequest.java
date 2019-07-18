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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.server.ServerService;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookSchedulerService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
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

    @Autowired
    public WorkbookSchedulerService workbookSchedulerService;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();

        ExchangeData dInit = new ExchangeData();
        dInit.setStationId(stationIdAsStr);
        dInit.setCommand(new Protocol.StationTaskStatus(Collections.emptyList()));
        ExchangeData d0 = serverService.processRequest(dInit, Consts.LOCALHOST_IP);

        assertNotNull(d0);
        assertNotNull(d0.getReAssignedStationId());
        assertNotNull(d0.getReAssignedStationId().sessionId);
        assertEquals(stationIdAsStr, d0.getReAssignedStationId().reAssignedStationId);

        String sessionId = d0.getReAssignedStationId().sessionId;

        List<Object[]> counts = taskRepository.getCountPerOrder(workbook.getId());
        for (Object[] count : counts) {
            if (((Number)count[0]).intValue() > 1) {
                break;
            }
            ExchangeData data = new ExchangeData();
            data.initRequestToLaunchpad(stationIdAsStr, sessionId);

            data.setCommand(new Protocol.RequestTask(false));


            Protocol.AssignedTask r = new Protocol.AssignedTask();
            WorkbookService.TasksAndAssignToStationResult result = workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
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
            data1.initRequestToLaunchpad(stationIdAsStr, sessionId);
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

            workbookSchedulerService.updateWorkbookStatus(
                    workbookRepository.findByIdForUpdate(workbook.id),
                    true);
            workbook = workbookCache.findById(workbook.id);
        }

        ExchangeData data = new ExchangeData();
        data.initRequestToLaunchpad(stationIdAsStr, sessionId);
        data.setCommand(new Protocol.RequestTask(false));

        ExchangeData d = serverService.processRequest(data, Consts.LOCALHOST_IP);
        assertNotNull(d);
        assertNotNull(d.getAssignedTask());
        assertNotNull(d.getAssignedTask().tasks);
        assertFalse(d.getAssignedTask().tasks.isEmpty());

        ExchangeData data1 = new ExchangeData();
        data1.initRequestToLaunchpad(stationIdAsStr, sessionId);
        data1.setCommand(new Protocol.RequestTask(false));

        ExchangeData d1 = serverService.processRequest(data1, Consts.LOCALHOST_IP);
        assertNotNull(d1);
        assertNotNull(d1.getAssignedTask());
        assertNull(d1.getAssignedTask().tasks);

    }

}
