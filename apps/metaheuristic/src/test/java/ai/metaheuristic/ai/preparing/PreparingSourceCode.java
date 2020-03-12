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
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.source_code.TaskCollector;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtilsV1;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtilsV1;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYamlV1;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ai.metaheuristic.api.data.source_code.SourceCodeApiData.TaskProducingResultComplex;
import static org.junit.Assert.*;

@Slf4j
public abstract class PreparingSourceCode extends PreparingExperiment {

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
    public ExecContextImpl execContextForFeature = null;

    public ExecContextParamsYaml execContextYaml;

    public Company company;

    public abstract String getSourceCodeYamlAsString();

    public static String getSourceCodeV1() {
        SourceCodeParamsYamlV1 planParamsYaml = new SourceCodeParamsYamlV1();
        planParamsYaml.source = new SourceCodeParamsYamlV1.SourceCodeV1();
        planParamsYaml.source.uid = "SourceCode for experiment";
//            globals:
//              - global-var
//            inline:
//              mh.hyper-params:
//                RNN: LSTM
//                batches: '40'
//                seed: '42'
//                time_steps: '7'

        planParamsYaml.source.variables = new SourceCodeParamsYamlV1.VariableDefinitionV1();
        planParamsYaml.source.variables.globals = List.of(PreparingSourceCode.TEST_GLOBAL_VARIABLE);
        planParamsYaml.source.variables.inline.put(ConstsApi.MH_HYPER_PARAMS, Map.of("RNN", "LSTM", "batches", "40", "seed", "42", "time_steps", "7"));
        {
            SourceCodeParamsYamlV1.ProcessV1 p = new SourceCodeParamsYamlV1.ProcessV1();
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1("function-01:1.1");
            p.inputs.add( new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, PreparingSourceCode.TEST_GLOBAL_VARIABLE));
            p.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "assembled-raw-output"));
//      input:
//        - variable: test-variable
//          sourcing: dispatcher
//      output:
//        - variable: assembled-raw-output
//          sourcing: dispatcher

            planParamsYaml.source.processes.add(p);
        }
        {
            SourceCodeParamsYamlV1.ProcessV1 p = new SourceCodeParamsYamlV1.ProcessV1();
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1("function-02:1.1");
            p.inputs.add( new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "assembled-raw-output"));
            p.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "dataset-processing-output"));

            planParamsYaml.source.processes.add(p);

            p.subProcesses = new SourceCodeParamsYamlV1.SubProcessesV1();
            p.subProcesses.logic = EnumsApi.SourceCodeSubProcessLogic.and;

            SourceCodeParamsYamlV1.ProcessV1 p1 = new SourceCodeParamsYamlV1.ProcessV1();
            p1.name = "feature-processing-1";
            p1.code = "feature-processing-1";
            p1.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1("function-03:1.1");
            p1.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "dataset-processing-output"));
            p1.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "feature-output-1"));

            SourceCodeParamsYamlV1.ProcessV1 p2 = new SourceCodeParamsYamlV1.ProcessV1();
            p2.name = "feature-processing-2";
            p2.code = "feature-processing-2";
            p2.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1("function-04:1.1");
            p2.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "dataset-processing-output"));
            p2.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "feature-output-2"));

            SourceCodeParamsYamlV1.ProcessV1 p3 = new SourceCodeParamsYamlV1.ProcessV1();
            p3.name = "feature-processing-3";
            p3.code = "feature-processing-3";
            p3.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1("function-05:1.1");
            p3.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "dataset-processing-output"));
            p3.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "feature-output-3"));

            p.subProcesses.processes = List.of(p1, p2);
        }
        {
//    - code: mh.permute-variables-and-hyper-params
//      name: permute variables and hyper params
//      metas:
//        - key: variables
//          value: feature-processing_matrix_of_winning, feature-processing_cluster_size_1
//      functions:
//        - code: mh.permute-variables-and-hyper-params
//      output:
//        - variable: feature-per-task
//          sourcing: dispatcher

            SourceCodeParamsYamlV1.ProcessV1 p = new SourceCodeParamsYamlV1.ProcessV1();
            p.name = "permute variables and hyper params";
            p.code = "mh.permute-variables-and-hyper-params";

            p.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1("mh.permute-variables-and-hyper-params", EnumsApi.FunctionExecContext.internal);
            p.metas = List.of(new Meta("variables", "feature-output-1,feature-output-2,feature-output-3", null));
            p.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "feature-per-task"));

            planParamsYaml.source.processes.add(p);

            p.subProcesses = new SourceCodeParamsYamlV1.SubProcessesV1();
            p.subProcesses.logic = EnumsApi.SourceCodeSubProcessLogic.sequential;

            SourceCodeParamsYamlV1.ProcessV1 p1 = new SourceCodeParamsYamlV1.ProcessV1();
            p1.name = "feature-processing-1";
            p1.code = "feature-processing-1";
            p1.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1(TEST_FIT_FUNCTION);
//            input:
//              - variable: feature-per-task
//                sourcing: dispatcher
//            output:
//              - variable: model
//                sourcing: dispatcher
            p1.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "dataset-processing-output"));
            p1.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "feature-per-task"));
            p1.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "model"));

            SourceCodeParamsYamlV1.ProcessV1 p2 = new SourceCodeParamsYamlV1.ProcessV1();
            p2.name = "feature-processing-2";
            p2.code = "feature-processing-2";
            p2.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1(TEST_PREDICT_FUNCTION);
            p2.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "dataset-processing-output"));
//              - variable: metrics
//                sourcing: dispatcher
//              - variable: predicted
//                sourcing: dispatcher
            p2.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "metrics"));
            p2.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "predicted"));

            SourceCodeParamsYamlV1.ProcessV1 p3 = new SourceCodeParamsYamlV1.ProcessV1();
            p3.name = "feature-processing-3";
            p3.code = "feature-processing-3";
            p3.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1(TEST_FITTING_FUNCTION);
            p3.inputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "predicted"));
            p3.outputs.add(new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher, "overfitting"));

            p.subProcesses.processes = List.of(p1, p2);
        }
        final SourceCodeParamsYamlUtilsV1 forVersion = (SourceCodeParamsYamlUtilsV1) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
        String yaml = Objects.requireNonNull(forVersion).toString(planParamsYaml);
        return yaml;
    }

    public String getSourceParamsYamlAsString_Simple() {
        return getSourceCodeV1();
    }

    public static final String TEST_GLOBAL_VARIABLE = "test-variable";

    @Before
    public void beforePreparingPlan() {
        assertTrue(globals.isUnitTesting);

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

        sourceCode = new SourceCodeImpl();
        sourceCode.setUid("test-sourceCode-code");

        SourceCodeStoredParamsYamlV1 sourceCodeStored = new SourceCodeStoredParamsYamlV1();
        sourceCodeStored.source = getSourceCodeYamlAsString();
        sourceCodeStored.lang = EnumsApi.SourceCodeLang.yaml;

        final SourceCodeStoredParamsYamlUtilsV1 forVersion = (SourceCodeStoredParamsYamlUtilsV1) SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
        assertNotNull(forVersion);
        String params = forVersion.toString(sourceCodeStored);

        sourceCode.setParams(params);
        sourceCode.setCreatedOn(System.currentTimeMillis());
        sourceCode.companyId = company.uniqueId;


        SourceCode tempSourceCode = sourceCodeRepository.findByUidAndCompanyId(sourceCode.getUid(), company.uniqueId);
        if (tempSourceCode !=null) {
            sourceCodeCache.deleteById(tempSourceCode.getId());
        }
        sourceCodeCache.save(sourceCode);

        byte[] bytes = "A resource for input pool".getBytes();

        try {
            globalVariableService.deleteByVariable(TEST_GLOBAL_VARIABLE);
        } catch (Throwable th) {
            log.error("error preparing variables", th);
        }

        globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-01.txt");
        globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-02.txt");
        globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-03.txt");

        execContextYaml = new ExecContextParamsYaml();
        execContextYaml.variables = new ExecContextParamsYaml.VariableDeclaration();
        execContextYaml.variables.globals = new ArrayList<>();
        execContextYaml.variables.globals.add(TEST_GLOBAL_VARIABLE);
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
//    value: '3'
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

    @After
    public void afterPreparingPlan() {
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
        if (execContextForFeature !=null) {
            try {
                execContextRepository.deleteById(execContextForFeature.getId());
            }
            catch (Throwable th) {
                log.error("Error while workbookRepository.deleteById()", th);
            }
            try {
                taskRepository.deleteByExecContextId(execContextForFeature.getId());
            } catch (ObjectOptimisticLockingFailureException th) {
                //
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

            EnumsApi.SourceCodeValidateStatus status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
            assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status);

            ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode);
            execContextForFeature = result.execContext;

            assertFalse(result.isErrorMessages());
            assertNotNull(execContextForFeature);
            assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForFeature.getState());


            EnumsApi.TaskProducingStatus producingStatus = execContextService.toProducing(execContextForFeature.id);
            assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);
            execContextForFeature = Objects.requireNonNull(execContextCache.findById(this.execContextForFeature.id));
            assertNotNull(execContextForFeature);
            assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForFeature.getState());
        }
        {
            SourceCodeApiData.TaskProducingResultComplex result1 = sourceCodeService.produceAllTasks(true, sourceCode, this.execContextForFeature);
            experiment = Objects.requireNonNull(experimentCache.findById(experiment.id));

            this.execContextForFeature = Objects.requireNonNull(execContextCache.findById(execContextForFeature.id));
            assertEquals(result1.numberOfTasks, taskRepository.findAllTaskIdsByExecContextId(execContextForFeature.id).size());
            assertEquals(result1.numberOfTasks, execContextService.getCountUnfinishedTasks(execContextForFeature));

            assertEquals(EnumsApi.TaskProducingStatus.OK, result1.taskProducingStatus);
            assertEquals(EnumsApi.ExecContextState.PRODUCED, EnumsApi.ExecContextState.toState(this.execContextForFeature.getState()));
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
