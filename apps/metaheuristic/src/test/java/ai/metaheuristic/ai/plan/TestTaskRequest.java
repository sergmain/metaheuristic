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
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.server.ServerService;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookSchedulerService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
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
        String sessionId;
        {
            final StationCommParamsYaml stationComm = new StationCommParamsYaml();
            stationComm.stationCommContext = new StationCommParamsYaml.StationCommContext(stationIdAsStr, null);
            stationComm.reportStationTaskStatus = new StationCommParamsYaml.ReportStationTaskStatus(Collections.emptyList());


            final String stationYaml = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm);
            String launchpadResponse = serverService.processRequest(stationYaml, "127.0.0.1");

            LaunchpadCommParamsYaml d0 = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

            assertNotNull(d0);
            assertNotNull(d0.getReAssignedStationId());
            assertNotNull(d0.getReAssignedStationId().sessionId);
            assertEquals(stationIdAsStr, d0.getReAssignedStationId().reAssignedStationId);

            sessionId = d0.getReAssignedStationId().sessionId;
        }
        List<Object[]> counts = taskRepository.getCountPerOrder(workbook.getId());
        for (Object[] count : counts) {
            if (((Number)count[0]).intValue() > 1) {
                break;
            }

            LaunchpadCommParamsYaml.AssignedTask t = workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
            assertNotNull(t);

            final StationCommParamsYaml stationComm0 = new StationCommParamsYaml();
            stationComm0.stationCommContext = new StationCommParamsYaml.StationCommContext(stationIdAsStr, sessionId);
            stationComm0.requestTask = new StationCommParamsYaml.RequestTask(false);

            final String stationYaml0 = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm0);
            String launchpadResponse0 = serverService.processRequest(stationYaml0, "127.0.0.1");

            LaunchpadCommParamsYaml d1 = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse0);
            assertNotNull(d1);
            assertNull(d1.getAssignedTask());

            finishCurrentWithOk(1);
            Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(t.taskId, true);
            assertEquals(Enums.UploadResourceStatus.OK, status);

            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            assertNotNull(task);
            assertTrue(task.isCompleted);

            workbookSchedulerService.updateWorkbookStatus(workbook.id,true);
            workbook = workbookCache.findById(workbook.id);
        }
        {
            final StationCommParamsYaml stationComm0 = new StationCommParamsYaml();
            stationComm0.stationCommContext = new StationCommParamsYaml.StationCommContext(stationIdAsStr, sessionId);
            stationComm0.requestTask = new StationCommParamsYaml.RequestTask(false);

            final String stationYaml0 = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm0);
            String launchpadResponse0 = serverService.processRequest(stationYaml0, Consts.LOCALHOST_IP);

            LaunchpadCommParamsYaml d = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse0);

            assertNotNull(d);
            assertNotNull(d.getAssignedTask());
            assertNotNull(d.getAssignedTask());
        }
        {
            final StationCommParamsYaml stationComm1 = new StationCommParamsYaml();
            stationComm1.stationCommContext = new StationCommParamsYaml.StationCommContext(stationIdAsStr, sessionId);
            stationComm1.requestTask = new StationCommParamsYaml.RequestTask(false);

            final String stationYaml1 = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm1);
            String launchpadResponse1 = serverService.processRequest(stationYaml1, Consts.LOCALHOST_IP);

            LaunchpadCommParamsYaml d1 = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse1);

            assertNotNull(d1);
            assertNull(d1.getAssignedTask());
        }
    }

}
