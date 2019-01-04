package aiai.ai.flow;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.server.ServerService;
import aiai.ai.preparing.FeatureMethods;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestTaskRequest extends FeatureMethods {

    @Autowired
    public ServerService serverService;

    @Override
    public String getFlowParamsAsYaml() {
        return getFlowParamsAsYaml_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();

        List<Object[]> counts = taskRepository.getCountPerOrder(flowInstance.getId());
        for (Object[] count : counts) {
            if (((Number)count[0]).intValue() > 1) {
                break;
            }
            ExchangeData data = new ExchangeData();
            data.setStationId(stationIdAsStr);
            data.setCommand(new Protocol.RequestTask(false));

            ExchangeData d = serverService.processRequest(data, Consts.LOCALHOST_IP);
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

            Task task = taskRepository.findById(t.taskId).orElse(null);
            assertNotNull(task);
            assertTrue(task.isCompleted);

            int order = flowInstance.producingOrder;
            flowInstance = flowService.markOrderAsProcessed(flowInstance);
            assertEquals(order + 1, flowInstance.producingOrder);
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
