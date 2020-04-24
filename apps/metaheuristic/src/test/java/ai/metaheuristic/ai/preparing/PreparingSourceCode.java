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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.source_code.TaskCollector;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtilsV1;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYamlV1;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ai.metaheuristic.api.data.source_code.SourceCodeApiData.TaskProducingResultComplex;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public abstract class PreparingSourceCode extends PreparingCore {

    private static final String TEST_SOURCE_CODE_CODE = "test-sourceCode-code";

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
    public FunctionCache functionCache;

    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public ExecContextService execContextService;

    @Autowired
    public TaskPersistencer taskPersistencer;

    @Autowired
    public CompanyRepository companyRepository;

    @Autowired
    public ExecContextGraphTopLevelService execContextGraphTopLevelService;

    @Autowired
    public ExecContextCreatorService execContextCreatorService;

    public SourceCodeImpl sourceCode = null;
    public Function s1 = null;
    public Function s2 = null;
    public Function s3 = null;
    public Function s4 = null;
    public Function s5 = null;
    public ExecContextImpl execContextForTest = null;

    public ExecContextParamsYaml execContextYaml;

    public Company company;
    public GlobalVariable testGlobalVariable;

    public abstract String getSourceCodeYamlAsString();

    @SneakyThrows
    public static String getSourceCodeV1() {
        return IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
    }

    public String getSourceParamsYamlAsString_Simple() {
        return getSourceCodeV1();
    }

    public static final String TEST_GLOBAL_VARIABLE = "test-variable";

    @BeforeEach
    public void beforePreparingSourceCode() {
        assertTrue(globals.isUnitTesting);

        cleanUp();

        company = new Company();
        company.name = "Test company #2";
        companyTopLevelService.addCompany(company);

        assertNotNull(company.id);
        assertNotNull(company.uniqueId);

        // id==1L must be assigned only to master company
        assertNotEquals(Consts.ID_1, company.id);

        s1 = createFunction("function-01:1.1");
        s2 = createFunction("function-02:1.1");
        s3 = createFunction("function-03:1.1");
        s4 = createFunction("function-04:1.1");
        s5 = createFunction("function-05:1.1");

        SourceCodeImpl sc = new SourceCodeImpl();
        sc.setUid(TEST_SOURCE_CODE_CODE);

        SourceCodeStoredParamsYamlV1 sourceCodeStored = new SourceCodeStoredParamsYamlV1();
        sourceCodeStored.source = getSourceCodeYamlAsString();

        // check correctness of sourceCode
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeStored.source);
        sourceCodeParamsYaml.checkIntegrity();

        sourceCodeStored.lang = EnumsApi.SourceCodeLang.yaml;

        final SourceCodeStoredParamsYamlUtilsV1 forVersion = (SourceCodeStoredParamsYamlUtilsV1) SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
        assertNotNull(forVersion);
        String params = forVersion.toString(sourceCodeStored);

        sc.setParams(params);
        sc.setCreatedOn(System.currentTimeMillis());
        sc.companyId = company.uniqueId;


        SourceCode tempSourceCode = sourceCodeRepository.findByUidAndCompanyId(sc.getUid(), company.uniqueId);
        if (tempSourceCode !=null) {
            sourceCodeCache.deleteById(tempSourceCode.getId());
        }
        sourceCode = sourceCodeCache.save(sc);

        byte[] bytes = "A resource for input pool".getBytes();

        try {
            globalVariableService.deleteByVariable(TEST_GLOBAL_VARIABLE);
        } catch (Throwable th) {
            log.error("error preparing variables", th);
        }

        testGlobalVariable = globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-01.txt");

        execContextYaml = new ExecContextParamsYaml();
        execContextYaml.variables.globals = new ArrayList<>();
        execContextYaml.variables.globals.add(TEST_GLOBAL_VARIABLE);
    }

    private void cleanUp() {
        SourceCode sc = sourceCodeRepository.findByUid(TEST_SOURCE_CODE_CODE);
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
                    taskRepository.deleteByExecContextId(execContextId);
                } catch (Throwable th) {
                    log.error("Error while taskRepository.deleteByRefIdAndRefType()", th);
                }
                try {
                    variableService.deleteByExecContextId(execContextId);
                } catch (Throwable th) {
                    log.error("error", th);
                }
            }
            try {
                sourceCodeCache.deleteById(sc.getId());
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
            globalVariableService.deleteByVariable(TEST_GLOBAL_VARIABLE);
        } catch (Throwable th) {
            log.error("error", th);
        }
    }

    private Function createFunction(String functionCode) {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.code = functionCode;
        sc.type = functionCode + "-type";
        sc.file = "some-file";
        sc.setEnv("env-"+functionCode);
        sc.sourcing = EnumsApi.FunctionSourcing.processor;

        sc.info = new FunctionConfigYaml.FunctionInfo(false, 1000);
//  metas:
//  - key: mh.task-params-version
//    value: '5'
        Objects.requireNonNull(sc.metas).add(new Meta(ConstsApi.META_MH_TASK_PARAMS_VERSION, "5", null));
        Function s = new Function();
        Long functionId = functionRepository.findIdByCode(functionCode);
        if (functionId!=null) {
            functionCache.delete(functionId);
        }
        s.setCode(functionCode);
        s.setType(sc.type);
        s.setParams(FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc));

        functionCache.save(s);
        return s;
    }

    @AfterEach
    public void afterPreparingSourceCode() {
        if (sourceCode !=null) {
            try {
                sourceCodeCache.deleteById(sourceCode.getId());
            } catch (Throwable th) {
                log.error("Error while planCache.deleteById()", th);
            }
        }
        if (company!=null) {
            try {
                companyRepository.deleteById(company.id);
            } catch (Throwable th) {
                log.error("Error while companyRepository.deleteById()", th);
            }
        }
        deleteFunction(s1);
        deleteFunction(s2);
        deleteFunction(s3);
        deleteFunction(s4);
        deleteFunction(s5);
        if (execContextForTest !=null) {
            try {
                execContextRepository.deleteById(execContextForTest.getId());
            }
            catch (Throwable th) {
                log.error("Error while workbookRepository.deleteById()", th);
            }
            try {
                taskRepository.deleteByExecContextId(execContextForTest.getId());
//            } catch (ObjectOptimisticLockingFailureException th) {

            } catch (Throwable th) {
                log.error("Error while taskRepository.deleteByRefIdAndRefType()", th);
            }
        }
        try {
            globalVariableService.deleteByVariable(TEST_GLOBAL_VARIABLE);
        } catch (Throwable th) {
            log.error("error", th);
        }
    }

    public TaskProducingResultComplex produceTasksForTest() {
        {
            SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());
            assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

            SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
            assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

            ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode, company.getUniqueId());
            execContextForTest = result.execContext;

            assertFalse(result.isErrorMessages());
            assertNotNull(execContextForTest);
            assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForTest.getState());


            EnumsApi.TaskProducingStatus producingStatus = execContextService.toProducing(execContextForTest.id);
            assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);
            execContextForTest = Objects.requireNonNull(execContextCache.findById(this.execContextForTest.id));
            assertNotNull(execContextForTest);
            assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForTest.getState());
        }
        {
            SourceCodeApiData.TaskProducingResultComplex result1 = sourceCodeService.produceAllTasks(true, sourceCode, this.execContextForTest);
            experiment = Objects.requireNonNull(experimentCache.findById(experiment.id));

            this.execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
            assertEquals(result1.numberOfTasks, taskRepository.findAllTaskIdsByExecContextId(execContextForTest.id).size());
            assertEquals(result1.numberOfTasks, execContextService.getCountUnfinishedTasks(execContextForTest));

            assertEquals(EnumsApi.TaskProducingStatus.OK, result1.taskProducingStatus);
            assertEquals(EnumsApi.ExecContextState.PRODUCED, EnumsApi.ExecContextState.toState(this.execContextForTest.getState()));
            return result1;
        }
    }

    private void deleteFunction(Function s) {
        if (s!=null) {
            try {
                functionCache.delete(s);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }
}
