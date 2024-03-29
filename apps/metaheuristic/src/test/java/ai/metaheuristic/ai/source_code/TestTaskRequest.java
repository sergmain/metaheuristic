/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
//@ActiveProfiles({"dispatcher", "mysql"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestTaskRequest extends FeatureMethods {

    @Autowired private TaskFinishingTopLevelService taskFinishingTopLevelService;
    @Autowired private TaskVariableTopLevelService taskVariableTopLevelService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextTaskStateService execContextTaskStateTopLevelService;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private SouthbridgeService serverService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextCache execContextCache;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testTaskRequest() {
        System.out.println("start step_0_0_produce_tasks_and_start()");
        step_0_0_produce_tasks_and_start();

        System.out.println("start step_1_0_init_session_id()");
        PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(processorIdAndCoreIds, preparingSourceCodeData, preparingCodeData);

        System.out.println("start step_2()");
        step_2(processorIdAndCoreIds);

        System.out.println("start step_3()");
        step_3(processorIdAndCoreIds);

        System.out.println("start step_4()");
        step_4(processorIdAndCoreIds);
    }



    private void step_2(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
//        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());


        final AtomicReference<DispatcherCommParamsYaml.AssignedTask> tRef = new AtomicReference<>();
        await()
            .atLeast(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(300))
            .with()
            .pollInterval(Duration.ofMillis(500))
            .until(()-> {
                final DispatcherCommParamsYaml.AssignedTask task = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
                tRef.set(task);
                return task!=null;
            });

        // get a task for processing
        //DispatcherCommParamsYaml.AssignedTask t = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        assertNotNull(tRef.get());

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = processorComm0.request;

        req0.processorCommContext = PreparingSourceCodeService.toProcessorCommContext(processorIdAndCoreIds);
        req0.cores.add(new ProcessorCommParamsYaml.Core("core-1", processorIdAndCoreIds.coreId1, new ProcessorCommParamsYaml.RequestTask(false, null)));
        req0.cores.add(new ProcessorCommParamsYaml.Core("core-2", processorIdAndCoreIds.coreId2, null));

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = serverService.processRequest(processorYaml0, "127.0.0.1");

        // get a task for processing one more time
        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);
        assertNotNull(d1);
        // there isn't a new task for processing
        // we will get the same task
        final DispatcherCommParamsYaml.DispatcherResponse response = d1.response;
        assertNotNull(response);
        assertNotNull(response.cores);
        assertEquals(2, response.cores.size());
        assertNotNull(response.cores.get(0).code);
        assertNotNull(response.cores.get(0).coreId);

        assertNotNull(response.cores.get(1).code);
        assertNotNull(response.cores.get(1).coreId);

        final DispatcherCommParamsYaml.AssignedTask assignedTask = response.cores.get(0).getAssignedTask();
        assertNotNull(assignedTask);
        assertEquals(tRef.get().taskId, assignedTask.taskId);

        storeConsoleResultAsOk(processorIdAndCoreIds);
        final TaskImpl task = taskRepository.findById(tRef.get().taskId).orElse(null);
        assertNotNull(task);

        TaskParamsYaml tpy = task.getTaskParamsYaml();
        TaskSyncService.getWithSyncVoid(task.id, () -> {
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Enums.UploadVariableStatus status = taskVariableTopLevelService.updateStatusOfVariable(task.id, output.id).status;
                assertEquals(Enums.UploadVariableStatus.OK, status);
            }
        });
        taskFinishingTopLevelService.checkTaskCanBeFinished(task.id);
        ExecContextTaskStateSyncService.getWithSyncNullable(getExecContextForTest().execContextTaskStateId,
            ()->execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(getExecContextForTest().id, getExecContextForTest().execContextTaskStateId));

        final TaskImpl task2 = taskRepository.findById(tRef.get().taskId).orElse(null);
        assertNotNull(task2);
        assertTrue(task2.completed!=0);

        execContextTopLevelService.updateExecContextStatus(getExecContextForTest().id);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
    }

    private void step_3(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
//        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req0 = processorComm0.request;

        req0.processorCommContext = PreparingSourceCodeService.toProcessorCommContext(processorIdAndCoreIds);
        req0.cores.add(new ProcessorCommParamsYaml.Core("core-1", processorIdAndCoreIds.coreId1, new ProcessorCommParamsYaml.RequestTask(false, null)));
        req0.cores.add(new ProcessorCommParamsYaml.Core("core-2", processorIdAndCoreIds.coreId2, null));

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = serverService.processRequest(processorYaml0, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);

        assertNotNull(d);
        final DispatcherCommParamsYaml.DispatcherResponse response = d.response;

        assertNotNull(response.cores);
        assertEquals(2, response.cores.size());
        assertNotNull(response.cores.get(0).code);
        assertNotNull(response.cores.get(0).coreId);

        assertNotNull(response.cores.get(1).code);
        assertNotNull(response.cores.get(1).coreId);
        final DispatcherCommParamsYaml.AssignedTask assignedTask = response.cores.get(0).getAssignedTask();
        //assertNotNull(assignedTask);
    }

    private void step_4(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        final ProcessorCommParamsYaml processorComm1 = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req1 = processorComm1.request;

        req1.processorCommContext = PreparingSourceCodeService.toProcessorCommContext(processorIdAndCoreIds);
        req1.cores.add(new ProcessorCommParamsYaml.Core("core-1", processorIdAndCoreIds.coreId1, new ProcessorCommParamsYaml.RequestTask(false, null)));
        req1.cores.add(new ProcessorCommParamsYaml.Core("core-2", processorIdAndCoreIds.coreId2, null));

        final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
        String dispatcherResponse1 = serverService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

        assertNotNull(d1);
        final DispatcherCommParamsYaml.DispatcherResponse response = d1.response;

        assertNotNull(response.cores);
        assertEquals(2, response.cores.size());
        assertNotNull(response.cores.get(0).code);
        assertNotNull(response.cores.get(0).coreId);

        assertNotNull(response.cores.get(1).code);
        assertNotNull(response.cores.get(1).coreId);
        final DispatcherCommParamsYaml.AssignedTask assignedTask = response.cores.get(0).getAssignedTask();
        assertNull(assignedTask);
    }

}
