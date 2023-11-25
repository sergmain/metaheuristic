/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueEvent;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTasksWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TransferStateFromTaskQueueToExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateUtils;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTxService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.preparing.PreparingConsts.GLOBAL_TEST_VARIABLE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 3:32 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class PreparingSourceCodeService {

    private final SourceCodeRepository sourceCodeRepository;
    private final CompanyRepository companyRepository;
    private final ExecContextRepository execContextRepository;
    private final TaskRepositoryForTest taskRepositoryForTest;
    private final TaskRepository taskRepository;
    private final SourceCodeTxService sourceCodeTxService;
    private final GlobalVariableTxService globalVariableService;
    private final TxSupportForTestingService txSupportForTestingService;
    private final SouthbridgeService serverService;
    private final ExecContextStatusService execContextStatusService;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final ExecContextCache execContextCache;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextTaskAssigningTopLevelService execContextTaskAssigningTopLevelService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;
    private final TaskWithInternalContextEventService taskWithInternalContextEventService;

    public void cleanUp(String sourceCodeUid) {
        SourceCode sc = sourceCodeRepository.findByUid(sourceCodeUid);
        Company c = null;
        if (sc!=null) {
            c = companyRepository.findByUniqueId(sc.getCompanyId());
            List<Long> execContextIds = execContextRepository.findIdsBySourceCodeId(sc.getId());

            for (Long execContextId : execContextIds) {
                try {
                    execContextRepository.deleteById(execContextId);
                }
                catch (Throwable th) {
                    log.error("Error while workbookRepository.deleteById()", th);
                }
                try {
                    taskRepositoryForTest.deleteByExecContextId(execContextId);
                } catch (Throwable th) {
                    log.error("Error while taskRepository.deleteByRefIdAndRefType()", th);
                }
                try {
                    txSupportForTestingService.deleteByExecContextId(execContextId);
                } catch (Throwable th) {
                    log.error("error", th);
                }
            }
            try {
                sourceCodeTxService.deleteSourceCodeById(sc.getId());
            } catch (Throwable th) {
                log.error("Error while planCache.deleteById()", th);
            }
        }

        if (c!=null) {
            try {
                companyRepository.deleteById(c.id);
            } catch (Throwable th) {
                log.error("Error while companyRepository.deleteById()", th);
            }
        }

        try {
            globalVariableService.deleteByVariable(GLOBAL_TEST_VARIABLE);
        } catch (Throwable th) {
            log.error("error", th);
        }
    }

    /**
     * this method must be called after   produceTasks() and after toStarted()
     * @return
     */
    public PreparingData.ProcessorIdAndCoreIds step_1_0_init_session_id(@Nullable Long processorId) {
        if (processorId==null) {
            throw new IllegalStateException("(processorId==null)");
        }
        KeepAliveRequestParamYaml processorComm = new KeepAliveRequestParamYaml();
        KeepAliveRequestParamYaml.Processor req = processorComm.processor;
        req.processorCode = ConstsApi.DEFAULT_PROCESSOR_CODE;

        req.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorId, null);
        processorComm.cores.addAll(getCoresKeepAlive(null));

        final String processorYaml = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = serverService.keepAlive(processorYaml, "127.0.0.1");

        KeepAliveResponseParamYaml d0 = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d0);
        assertNotNull(d0.response);
        final KeepAliveResponseParamYaml.ReAssignedProcessorId reAssignedProcessorId = d0.response.getReAssignedProcessorId();
        assertNotNull(reAssignedProcessorId);
        assertNotNull(reAssignedProcessorId.sessionId);
        assertEquals(processorId.toString(), reAssignedProcessorId.reAssignedProcessorId);

        assertNotNull(d0.response.coreInfos);
        assertEquals(2, d0.response.coreInfos.size());
        assertEquals("core-1", d0.response.coreInfos.get(0).code);
        assertEquals("core-2", d0.response.coreInfos.get(1).code);
        assertNotNull(d0.response.coreInfos.get(0).coreId);
        assertNotNull(d0.response.coreInfos.get(1).coreId);

        PreparingData.ProcessorIdAndCoreIds result = new PreparingData.ProcessorIdAndCoreIds(
                Long.valueOf(reAssignedProcessorId.reAssignedProcessorId), reAssignedProcessorId.sessionId,
                d0.response.coreInfos.get(0).coreId, d0.response.coreInfos.get(1).coreId);

        return result;
    }

    private static List<KeepAliveRequestParamYaml.Core> getCoresKeepAlive(@Nullable PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        return List.of(
                new KeepAliveRequestParamYaml.Core("/home/core-1", processorIdAndCoreIds==null ? null : processorIdAndCoreIds.coreId1, "core-1", null),
                new KeepAliveRequestParamYaml.Core("/home/core-2", processorIdAndCoreIds==null ? null : processorIdAndCoreIds.coreId2, "core-2", null)
        );
    }

    public static ProcessorCommParamsYaml.ProcessorCommContext toProcessorCommContext(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        return new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAndCoreIds.processorId, processorIdAndCoreIds.sessionId);
    }

    public void step_1_1_register_function_statuses(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds, PreparingData.PreparingSourceCodeData preparingSourceCodeData, PreparingData.PreparingCodeData preparingCodeData) {
        assertNotNull(processorIdAndCoreIds.processorId);
        assertEquals(preparingCodeData.processor.getId(), processorIdAndCoreIds.processorId);

        KeepAliveRequestParamYaml karpy = new KeepAliveRequestParamYaml();
/*
        karpy.functions.statuses.put(EnumsApi.FunctionState.ready, asListOfReady(preparingSourceCodeData.getF1(), preparingSourceCodeData.getF2(), preparingSourceCodeData.getF3(), preparingSourceCodeData.getF4(), preparingSourceCodeData.getF5(), preparingCodeData.getFitFunction(), preparingCodeData.getPredictFunction()));
*/

        KeepAliveRequestParamYaml.Processor pr = karpy.processor;
        pr.processorCode = ConstsApi.DEFAULT_PROCESSOR_CODE;
        karpy.cores.addAll(getCoresKeepAlive(processorIdAndCoreIds));

        final KeepAliveRequestParamYaml.Env env = new KeepAliveRequestParamYaml.Env();
        env.envs.put("env-for-test-function", "/path/to/cmd");
        env.envs.put("python-3", "/path/to/python-3");
        env.envs.put("java-17", "/path/to/java-17");

        pr.status = new KeepAliveRequestParamYaml.ProcessorStatus(
                env,
                new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.installed, "Git 1.0.0", null),
                "0:00 - 23:59",
                "[unknown]", "[unknown]", true,
                1, EnumsApi.OS.unknown, "/users/yyy", null);
        pr.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorIdAndCoreIds.processorId, processorIdAndCoreIds.sessionId);

        String yamlRequest = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(karpy);
        String yamlResponse = serverService.keepAlive(yamlRequest, "127.0.0.1");
        KeepAliveResponseParamYaml response = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.to(yamlResponse);
        assertTrue(response.success);
        int i =0;
    }

    private static String asListOfReady(Function... fs) {
        String codes = Arrays.stream(fs).map(f -> f.code).collect(Collectors.joining(","));
        return codes;
    }

    @SneakyThrows
    public void findTaskForRegisteringInQueueAndWait(ExecContextImpl execContext) {
        eventPublisher.publishEvent(new TransferStateFromTaskQueueToExecContextEvent(execContext.id, execContext.execContextTaskStateId));
        Thread.sleep(500);

        execContextTaskAssigningTopLevelService.putToQueue(new FindUnassignedTasksAndRegisterInQueueEvent());
        //execContextTaskAssigningTopLevelService.procesEvent();

        Thread.sleep(500);
        eventPublisher.publishEvent(new TransferStateFromTaskQueueToExecContextEvent(execContext.id, execContext.execContextTaskStateId));
        Thread.sleep(500);
//        execContextTaskAssigningTopLevelService.findUnassignedTasksAndRegisterInQueue(execContext.id);

        execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery(new ResetTasksWithErrorEvent(execContext.id));

/*
        boolean isQueueEmpty = true;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            isQueueEmpty = TaskProviderTopLevelService.allTaskGroupFinished(execContext.id);
            if (!isQueueEmpty) {
                break;
            }
        }
        assertFalse(isQueueEmpty);
*/
    }

    @SneakyThrows
    public void findRegisterInternalTaskInQueue(Long execContextId) {
        taskWithInternalContextEventService.MULTI_TENANTED_QUEUE.registerProcessSuspender(() -> true);
        execContextTaskAssigningTopLevelService.putToQueue(new FindUnassignedTasksAndRegisterInQueueEvent());

        await()
            .atLeast(Duration.ofMillis(0))
            .atMost(Duration.ofSeconds(10))
            .with()
            .pollInterval(Duration.ofMillis(500))
            .until(() -> taskWithInternalContextEventService.MULTI_TENANTED_QUEUE.isNotEmpty(execContextId));

        taskWithInternalContextEventService.MULTI_TENANTED_QUEUE.deRegisterProcessSuspender();
        taskWithInternalContextEventService.MULTI_TENANTED_QUEUE.processPoolOfExecutors(execContextId);

        await()
            .atLeast(Duration.ofMillis(0))
            .atMost(Duration.ofSeconds(10))
            .with()
            .pollInterval(Duration.ofMillis(100))
            .until(() -> taskWithInternalContextEventService.MULTI_TENANTED_QUEUE.isEmpty(execContextId));
    }

    @SneakyThrows
    public void waitUntilTaskFinished(Long taskId) {
        await()
            .atLeast(Duration.ofMillis(0))
            .atMost(Duration.ofSeconds(100))
            .with()
            .pollInterval(Duration.ofMillis(500))
            .until(() -> taskRepository.findById(taskId).filter(t-> EnumsApi.TaskExecState.isFinishedStateIncludingRecovery(t.execState)).isPresent());
    }

    @SneakyThrows
    public void findTaskForRegisteringInQueue(Long execContextId) {
        execContextTaskAssigningTopLevelService.putToQueue(new FindUnassignedTasksAndRegisterInQueueEvent());

        await()
            .atLeast(Duration.ofMillis(0))
            .atMost(Duration.ofSeconds(100))
            .with()
            .pollInterval(Duration.ofMillis(100))
            .until(()-> TaskProviderTopLevelService.allTaskGroupFinished(execContextId));
    }

    public ExecContextCreatorService.ExecContextCreationResult createExecContextForTest(PreparingData.PreparingSourceCodeData preparingSourceCodeData) {
        DispatcherContext context = new DispatcherContext(preparingSourceCodeData.getAccount(), preparingSourceCodeData.getCompany());
        return txSupportForTestingService.createExecContext(preparingSourceCodeData.getSourceCode(), context.asUserExecContext());
    }

    public void produceTasksForTest(String sourceCodeParams, PreparingData.PreparingSourceCodeData preparingSourceCodeData ) {
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeParams);
        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        System.out.println("start checkConsistencyOfSourceCode()");
        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(preparingSourceCodeData.getSourceCode());
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = createExecContextForTest(preparingSourceCodeData);
        preparingSourceCodeData.setExecContextForTest(result.execContext);
        ExecContextSyncService.getWithSyncVoid(preparingSourceCodeData.getExecContextForTest().id, () -> {

            assertFalse(result.isErrorMessages());
            assertNotNull(preparingSourceCodeData.getExecContextForTest());
            assertEquals(EnumsApi.ExecContextState.NONE.code, preparingSourceCodeData.getExecContextForTest().getState());


            preparingSourceCodeData.setExecContextForTest(Objects.requireNonNull(execContextCache.findById(preparingSourceCodeData.getExecContextForTest().id)));
            assertNotNull(preparingSourceCodeData.getExecContextForTest());

            assertEquals(EnumsApi.ExecContextState.STARTED.code, preparingSourceCodeData.getExecContextForTest().getState());
            ExecContextGraphSyncService.getWithSyncVoid(preparingSourceCodeData.getExecContextForTest().execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSyncVoid(preparingSourceCodeData.getExecContextForTest().execContextTaskStateId, ()-> {
                        txSupportForTestingService.produceAndStartAllTasks(preparingSourceCodeData.getSourceCode(), result.execContext.id);
                    }));
        });

        final ExecContextImpl byId = execContextCache.findById(preparingSourceCodeData.getExecContextForTest().id, true);
        final ExecContextImpl byId1 = execContextCache.findById(preparingSourceCodeData.getExecContextForTest().id);
        preparingSourceCodeData.setExecContextForTest(Objects.requireNonNull(byId));
        assertEquals(EnumsApi.ExecContextState.STARTED, EnumsApi.ExecContextState.toState(preparingSourceCodeData.getExecContextForTest().getState()));
        execContextStatusService.resetStatus();
        assertEquals(EnumsApi.ExecContextState.STARTED, execContextStatusService.getExecContextState(preparingSourceCodeData.getExecContextForTest().id));
    }

    public long getCountUnfinishedTasks(ExecContextImpl execContext) {
        if (execContext.execContextTaskStateId==null) {
            return 0;
        }
        ExecContextTaskState ects = execContextTaskStateRepository.findById(execContext.execContextTaskStateId).orElse(null);
        if (ects==null) {
            return 0;
        }
        return ExecContextTaskStateUtils.getCountUnfinishedTasks(ects.getExecContextTaskStateParamsYaml());
    }

    @Nullable
    public EnumsApi.TaskExecState findTaskState(ExecContextImpl execContext, Long taskId) {
        if (execContext.execContextTaskStateId==null) {
            return EnumsApi.TaskExecState.NONE;
        }
        ExecContextTaskState ects = execContextTaskStateRepository.findById(execContext.execContextTaskStateId).orElse(null);
        if (ects==null) {
            return EnumsApi.TaskExecState.NONE;
        }

        return ects.getExecContextTaskStateParamsYaml().states.getOrDefault(taskId, EnumsApi.TaskExecState.NONE);
    }



}
