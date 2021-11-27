/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.source_code.TaskCollector;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("WeakerAccess")
@Slf4j
public abstract class PreparingSourceCode extends PreparingCore {

    @Autowired
    public CompanyTopLevelService companyTopLevelService;

    @Autowired
    public SourceCodeCache sourceCodeCache;

    @Autowired
    public SourceCodeRepository sourceCodeRepository;

    @Autowired
    public SourceCodeValidationService sourceCodeValidationService;

    @Autowired
    public ExecContextRepository execContextRepository;

    @Autowired
    public ExecContextCache execContextCache;

    @Autowired
    public SourceCodeService sourceCodeService;

    @Autowired
    public SourceCodeTopLevelService sourceCodeTopLevelService;

    @Autowired
    public FunctionCache functionCache;

    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public ExecContextService execContextService;

    @Autowired
    public TaskTransactionalService taskTransactionalService;

    @Autowired
    public CompanyRepository companyRepository;

    @Autowired
    public ExecContextGraphTopLevelService execContextGraphTopLevelService;

    @Autowired
    public ExecContextCreatorService execContextCreatorService;

    @Autowired
    public ExecContextGraphService execContextGraphService;

    @Autowired
    public TaskProducingService taskProducingService;

    @Autowired
    public TaskTopLevelService taskTopLevelService;

    @Autowired
    public ExecContextTopLevelService execContextTopLevelService;

    @Autowired
    public ExecContextTaskProducingService execContextTaskProducingService;

    @Autowired
    public ExecContextTaskStateService execContextTaskStateService;

    @Autowired
    public ExecContextTaskStateCache execContextTaskStateCache;

    @Autowired
    public ExecContextGraphCache execContextGraphCache;

    @Autowired
    public VariableRepository variableRepository;

    @Autowired
    public TxSupportForTestingService txSupportForTestingService;

    @Autowired
    public TaskProviderTopLevelService taskProviderService;

    @Autowired
    public SouthbridgeService serverService;

    @Autowired
    public ExecContextTaskStateTopLevelService execContextTaskStateTopLevelService;

    @Autowired
    public ExecContextGraphRepository execContextGraphRepository;

    @Autowired
    public ExecContextTaskStateRepository execContextTaskStateRepository;

    @Autowired
    protected TaskRepositoryForTest taskRepositoryForTest;

    @Autowired
    protected ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;

    public SourceCodeImpl sourceCode = null;
    public Function f1 = null;
    public Function f2 = null;
    public Function f3 = null;
    public Function f4 = null;
    public Function f5 = null;
    public ExecContextImpl execContextForTest = null;

    public ExecContextParamsYaml execContextYaml;

    public Company company;
    public GlobalVariable testGlobalVariable;

    public abstract String getSourceCodeYamlAsString();

    @SneakyThrows
    public static String getSourceCodeV1() {
        return IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
    }

    public static String getSourceParamsYamlAsString_Simple() {
        return getSourceCodeV1();
    }

    public static final String GLOBAL_TEST_VARIABLE = "global-test-variable";

    @BeforeEach
    public void beforePreparingSourceCode() {
        assertTrue(globals.testing);
        assertNotSame(globals.dispatcher.asset.mode, EnumsApi.DispatcherAssetMode.replicated);

        String params = getSourceCodeYamlAsString();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(params);
        sourceCodeParamsYaml.checkIntegrity();

        cleanUp(sourceCodeParamsYaml.source.uid);

        company = new Company();
        company.name = "Test company #2";
        companyTopLevelService.addCompany(company);
        company = Objects.requireNonNull(companyTopLevelService.getCompanyByUniqueId(company.uniqueId));

        assertNotNull(company.id);
        assertNotNull(company.uniqueId);

        // id==1L must be assigned only to master company
        assertNotEquals(Consts.ID_1, company.id);

        f1 = createFunction("function-01:1.1");
        f2 = createFunction("function-02:1.1");
        f3 = createFunction("function-03:1.1");
        f4 = createFunction("function-04:1.1");
        f5 = createFunction("function-05:1.1");

        SourceCodeApiData.SourceCodeResult scr = sourceCodeTopLevelService.createSourceCode(getSourceCodeYamlAsString(), company.uniqueId);
        sourceCode = Objects.requireNonNull(sourceCodeCache.findById(scr.id));

        byte[] bytes = "A resource for input pool".getBytes();

        try {
            globalVariableService.deleteByVariable(GLOBAL_TEST_VARIABLE);
        } catch (Throwable th) {
            log.error("error preparing variables", th);
        }

        testGlobalVariable = globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, GLOBAL_TEST_VARIABLE,"file-01.txt");

        execContextYaml = new ExecContextParamsYaml();
        execContextYaml.variables.globals = new ArrayList<>();
        execContextYaml.variables.globals.add(GLOBAL_TEST_VARIABLE);
    }

    protected DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor_mustBeNewTask() {
        long mills;

        mills = System.currentTimeMillis();

        findTaskForRegisteringInQueueAndWait(execContextForTest.id);

        // get a task for processing
        log.info("Start experimentService.getTaskAndAssignToProcessor()");
        DispatcherCommParamsYaml.AssignedTask task = taskProviderService.findTask(processor.getId(), false);
        log.info("experimentService.getTaskAndAssignToProcessor() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        assertNotNull(task);
        return task;
    }

    /**
     * this method must be called after   produceTasks() and after toStarted()
     * @return
     */
    public String step_1_0_init_session_id() {
        String sessionId;
        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        req.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, null);

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = serverService.processRequest(processorYaml, "127.0.0.1");

        DispatcherCommParamsYaml d0 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d0);
        assertNotNull(d0.responses);
        assertEquals(1, d0.responses.size());
        final DispatcherCommParamsYaml.ReAssignProcessorId reAssignedProcessorId = d0.responses.get(0).getReAssignedProcessorId();
        assertNotNull(reAssignedProcessorId);
        assertNotNull(reAssignedProcessorId.sessionId);
        assertEquals(processorIdAsStr, reAssignedProcessorId.reAssignedProcessorId);

        sessionId = reAssignedProcessorId.sessionId;
        return sessionId;
    }

    public void step_1_1_register_function_statuses(String sessionId) {
        KeepAliveRequestParamYaml karpy = new KeepAliveRequestParamYaml();
        karpy.functions.statuses = asListOfReady(f1, f2, f3, f4, f5, fitFunction, predictFunction);

        KeepAliveRequestParamYaml.ProcessorRequest pr = new KeepAliveRequestParamYaml.ProcessorRequest();
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
            list.add(new KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status(function.code, Enums.FunctionState.ready));
        }
        return list;
    }

    @SuppressWarnings("WeakerAccess")
    @SneakyThrows
    public void findTaskForRegisteringInQueueAndWait(Long execContextId) {
        execContextTopLevelService.findTaskForRegisteringInQueue(execContextId);

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

    private Function createFunction(String functionCode) {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.code = functionCode;
        sc.type = functionCode + "-type";
        sc.file = "some-file";
        sc.env = "env-for-test-function";
        sc.sourcing = EnumsApi.FunctionSourcing.processor;

//  metas:
//  - mh.task-params-version: '5'
        Objects.requireNonNull(sc.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "5"));
        Long functionId = functionRepository.findIdByCode(functionCode);
        if (functionId!=null) {
            txSupportForTestingService.deleteFunctionById(functionId);
        }

        byte[] bytes = "some code for testing".getBytes();
        Function f = functionService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);
        return f;
    }

    @AfterEach
    public void afterPreparingSourceCode() {
        try {
            if (sourceCode!=null) {
                sourceCodeService.deleteSourceCodeById(sourceCode.getId());
            }
        } catch (Throwable th) {
            log.error("Error while planCache.deleteById()", th);
        }
        if (company!=null && company.id!=null) {
            try {
                companyRepository.deleteById(company.id);
            } catch (Throwable th) {
                log.error("Error while companyRepository.deleteById()", th);
            }
        }
        deleteFunction(f1);
        deleteFunction(f2);
        deleteFunction(f3);
        deleteFunction(f4);
        deleteFunction(f5);
        if (execContextForTest != null) {
            if (execContextCache.findById(execContextForTest.getId()) != null) {
                try {
                    execContextRepository.deleteById(execContextForTest.getId());
                } catch (Throwable th) {
                    log.error("Error while execContextRepository.deleteById()", th);
                }
            }
            if (execContextForTest.execContextGraphId!=null) {
                try {
                    execContextGraphRepository.deleteById(execContextForTest.execContextGraphId);
                } catch (Throwable th) {
                    log.error("Error while execContextGraphRepository.deleteById()", th);
                }
            }
            if (execContextForTest.execContextTaskStateId!=null) {
                try {
                    execContextTaskStateRepository.deleteById(execContextForTest.execContextTaskStateId);
                } catch (Throwable th) {
                    log.error("Error while execContextTaskStateRepository.deleteById()", th);
                }
            }
            taskRepositoryForTest.deleteByExecContextId(execContextForTest.getId());
        }
        try {
            globalVariableService.deleteByVariable(GLOBAL_TEST_VARIABLE);
        } catch (Throwable th) {
            log.error("error", th);
        }
    }

    public void produceTasksForTest() {
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());
        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = createExecContextForTest();
        execContextForTest = result.execContext;
        ExecContextSyncService.getWithSync(execContextForTest.id, () -> {

            assertFalse(result.isErrorMessages());
            assertNotNull(execContextForTest);
            assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForTest.getState());


            EnumsApi.TaskProducingStatus producingStatus = txSupportForTestingService.toProducing(execContextForTest.id);
            assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);
            execContextForTest = Objects.requireNonNull(execContextService.findById(this.execContextForTest.id));
            assertNotNull(execContextForTest);
            assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForTest.getState());
            ExecContextParamsYaml execContextParamsYaml = result.execContext.getExecContextParamsYaml();
            ExecContextGraphSyncService.getWithSync(execContextForTest.execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSync(execContextForTest.execContextTaskStateId, ()-> {
                        txSupportForTestingService.produceAndStartAllTasks(sourceCode, result.execContext.id, execContextParamsYaml);
                        return null;
                    }));

            return null;
        });

        this.execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

        assertEquals(EnumsApi.ExecContextState.STARTED, EnumsApi.ExecContextState.toState(this.execContextForTest.getState()));
    }

    public ExecContextCreatorService.ExecContextCreationResult createExecContextForTest() {
        return txSupportForTestingService.createExecContext(sourceCode, company.getUniqueId());
    }

    private void deleteFunction(@Nullable Function s) {
        if (s!=null) {
            try {
                txSupportForTestingService.deleteFunctionById(s.id);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
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

    public List<Long> getUnfinishedTaskVertices(ExecContextImpl execContext) {
        if (execContext.execContextTaskStateId==null) {
            return List.of();
        }
        ExecContextTaskState ects = execContextTaskStateCache.findById(execContext.execContextTaskStateId);
        if (ects==null) {
            return List.of();
        }
        return ExecContextTaskStateService.getUnfinishedTaskVertices(ects);
    }

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextImpl execContext) {
        if (execContext.execContextGraphId==null) {
            return List.of();
        }
        ExecContextGraph ecg = execContextGraphCache.findById(execContext.execContextGraphId);
        if (ecg==null) {
            return List.of();
        }
        return ExecContextGraphService.findLeafs(ecg);
    }

    public Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextImpl execContext, ExecContextData.TaskVertex vertex) {
        if (execContext.execContextGraphId==null) {
            return Set.of();
        }
        ExecContextGraph ecg = execContextGraphCache.findById(execContext.execContextGraphId);
        if (ecg==null) {
            return Set.of();
        }
        return ExecContextGraphService.findDirectAncestors(ecg, vertex);
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

    public Set<EnumsApi.TaskExecState> findTaskStates(ExecContextImpl execContext) {
        if (execContext.execContextTaskStateId==null) {
            return Set.of();
        }
        ExecContextTaskState ects = execContextTaskStateCache.findById(execContext.execContextTaskStateId);
        if (ects==null) {
            return Set.of();
        }

        return new HashSet<>(ects.getExecContextTaskStateParamsYaml().states.values());
    }


}
