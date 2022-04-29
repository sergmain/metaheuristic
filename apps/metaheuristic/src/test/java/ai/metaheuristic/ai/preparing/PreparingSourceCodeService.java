/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ai.metaheuristic.ai.preparing.PreparingConsts.GLOBAL_TEST_VARIABLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 3:32 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class PreparingSourceCodeService {

    private final SourceCodeRepository sourceCodeRepository;
    private final CompanyRepository companyRepository;
    private final ExecContextRepository execContextRepository;
    private final TaskRepositoryForTest taskRepositoryForTest;
    private final SourceCodeService sourceCodeService;
    private final GlobalVariableService globalVariableService;
    private final TxSupportForTestingService txSupportForTestingService;
    private final SouthbridgeService serverService;
    private final ExecContextStatusService execContextStatusService;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final ExecContextService execContextService;
    private final ExecContextTaskStateCache execContextTaskStateCache;
    private final ExecContextTaskAssigningTopLevelService execContextTaskAssigningTopLevelService;

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
                sourceCodeService.deleteSourceCodeById(sc.getId());
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
    public String step_1_0_init_session_id(@Nullable String processorIdAsStr) {
        if (processorIdAsStr==null) {
            throw new IllegalStateException("(processorIdAsStr==null)");
        }
        String sessionId;
        KeepAliveRequestParamYaml processorComm = new KeepAliveRequestParamYaml();
        KeepAliveRequestParamYaml.Processor req = new KeepAliveRequestParamYaml.Processor(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        req.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorIdAsStr, null);

        final String processorYaml = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = serverService.keepAlive(processorYaml, "127.0.0.1");

        KeepAliveResponseParamYaml d0 = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d0);
        assertNotNull(d0.responses);
        assertEquals(1, d0.responses.size());
        final KeepAliveResponseParamYaml.ReAssignedProcessorId reAssignedProcessorId = d0.responses.get(0).getReAssignedProcessorId();
        assertNotNull(reAssignedProcessorId);
        assertNotNull(reAssignedProcessorId.sessionId);
        assertEquals(processorIdAsStr, reAssignedProcessorId.reAssignedProcessorId);

        sessionId = reAssignedProcessorId.sessionId;
        return sessionId;
    }

    public void step_1_1_register_function_statuses(String sessionId, @Nullable String processorIdAsStr, PreparingData.PreparingSourceCodeData preparingSourceCodeData, PreparingData.PreparingCodeData preparingCodeData) {
        if (S.b(processorIdAsStr)) {
            throw new IllegalStateException("(S.b(processorIdAsStr))");
        }
        KeepAliveRequestParamYaml karpy = new KeepAliveRequestParamYaml();
        karpy.functions.statuses = asListOfReady(preparingSourceCodeData.getF1(), preparingSourceCodeData.getF2(), preparingSourceCodeData.getF3(), preparingSourceCodeData.getF4(), preparingSourceCodeData.getF5(), preparingCodeData.getFitFunction(), preparingCodeData.getPredictFunction());

        KeepAliveRequestParamYaml.Processor pr = new KeepAliveRequestParamYaml.Processor();
        pr.processorCode = ConstsApi.DEFAULT_PROCESSOR_CODE;
        final KeepAliveRequestParamYaml.Env env = new KeepAliveRequestParamYaml.Env();
        env.envs.put("env-for-test-function", "/path/to/cmd");
        env.envs.put("python-3", "/path/to/python-3");

        pr.processor = new KeepAliveRequestParamYaml.ReportProcessor(
                env,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null),
                "0:00 - 23:59",
                sessionId,
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, true,
                1, EnumsApi.OS.unknown, "/users/yyy");
        pr.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        karpy.requests.add(pr);

        String yamlRequest = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(karpy);
        String yamlResponse = serverService.keepAlive(yamlRequest, "127.0.0.1");
        KeepAliveResponseParamYaml response = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.to(yamlResponse);
        int i =0;
    }

    private static List<KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status> asListOfReady(Function... f) {
        List<KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status> list = new ArrayList<>();
        for (Function function : f) {
            list.add(new KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status(function.code, EnumsApi.FunctionState.ready));
        }
        return list;
    }

    @SneakyThrows
    public void findTaskForRegisteringInQueueAndWait(Long execContextId) {
        execContextTaskAssigningTopLevelService.findUnassignedTasksAndRegisterInQueue(execContextId);

        boolean isQueueEmpty = true;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2_000);
            isQueueEmpty = TaskProviderTopLevelService.allTaskGroupFinished(execContextId);
            if (!isQueueEmpty) {
                break;
            }
        }
        assertFalse(isQueueEmpty);
    }

    @SneakyThrows
    public void findInternalTaskForRegisteringInQueue(Long execContextId) {
        execContextTaskAssigningTopLevelService.findUnassignedTasksAndRegisterInQueue(execContextId);

        boolean isQueueEmpty = true;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2_000);
            isQueueEmpty = TaskProviderTopLevelService.allTaskGroupFinished(execContextId);
            if (isQueueEmpty) {
                break;
            }
        }
        assertTrue(isQueueEmpty);
    }

    public ExecContextCreatorService.ExecContextCreationResult createExecContextForTest(PreparingData.PreparingSourceCodeData preparingSourceCodeData) {
        return txSupportForTestingService.createExecContext(preparingSourceCodeData.getSourceCode(), preparingSourceCodeData.getCompany().getUniqueId());
    }

    public void produceTasksForTest(String sourceCodeParams, PreparingData.PreparingSourceCodeData preparingSourceCodeData ) {
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeParams);
        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(preparingSourceCodeData.getSourceCode());
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = createExecContextForTest(preparingSourceCodeData);
        preparingSourceCodeData.setExecContextForTest(result.execContext);
        ExecContextSyncService.getWithSync(preparingSourceCodeData.getExecContextForTest().id, () -> {

            assertFalse(result.isErrorMessages());
            assertNotNull(preparingSourceCodeData.getExecContextForTest());
            assertEquals(EnumsApi.ExecContextState.NONE.code, preparingSourceCodeData.getExecContextForTest().getState());


            EnumsApi.TaskProducingStatus producingStatus = txSupportForTestingService.toProducing(preparingSourceCodeData.getExecContextForTest().id);
            assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);
            preparingSourceCodeData.setExecContextForTest(Objects.requireNonNull(execContextService.findById(preparingSourceCodeData.getExecContextForTest().id)));
            assertNotNull(preparingSourceCodeData.getExecContextForTest());
            assertEquals(EnumsApi.ExecContextState.PRODUCING.code, preparingSourceCodeData.getExecContextForTest().getState());
            ExecContextParamsYaml execContextParamsYaml = result.execContext.getExecContextParamsYaml();
            ExecContextGraphSyncService.getWithSync(preparingSourceCodeData.getExecContextForTest().execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSync(preparingSourceCodeData.getExecContextForTest().execContextTaskStateId, ()-> {
                        txSupportForTestingService.produceAndStartAllTasks(preparingSourceCodeData.getSourceCode(), result.execContext.id, execContextParamsYaml);
                        return null;
                    }));

            return null;
        });

        preparingSourceCodeData.setExecContextForTest(Objects.requireNonNull(execContextService.findById(preparingSourceCodeData.getExecContextForTest().id)));
        assertEquals(EnumsApi.ExecContextState.STARTED, EnumsApi.ExecContextState.toState(preparingSourceCodeData.getExecContextForTest().getState()));
        execContextStatusService.resetStatus();
        assertEquals(EnumsApi.ExecContextState.STARTED, execContextStatusService.getExecContextState(preparingSourceCodeData.getExecContextForTest().id));
    }

    public long getCountUnfinishedTasks(ExecContextImpl execContext) {
        if (execContext.execContextTaskStateId==null) {
            return 0;
        }
        ExecContextTaskState ects = execContextTaskStateCache.findById(execContext.execContextTaskStateId);
        if (ects==null) {
            return 0;
        }
        return ExecContextTaskStateService.getCountUnfinishedTasks(ects);
    }

    @Nullable
    public EnumsApi.TaskExecState findTaskState(ExecContextImpl execContext, Long taskId) {
        if (execContext.execContextTaskStateId==null) {
            return EnumsApi.TaskExecState.NONE;
        }
        ExecContextTaskState ects = execContextTaskStateCache.findById(execContext.execContextTaskStateId);
        if (ects==null) {
            return EnumsApi.TaskExecState.NONE;
        }

        return ects.getExecContextTaskStateParamsYaml().states.getOrDefault(taskId, EnumsApi.TaskExecState.NONE);
    }



}
