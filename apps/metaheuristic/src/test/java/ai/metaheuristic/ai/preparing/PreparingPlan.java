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
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.company.CompanyTopLevelService;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeService;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import ai.metaheuristic.ai.launchpad.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextCache;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextService;
import ai.metaheuristic.ai.source_code.TaskCollector;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtilsV1;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.data.source_code.SourceCodeApiData.TaskProducingResultComplex;
import static org.junit.Assert.*;

@Slf4j
public abstract class PreparingPlan extends PreparingExperiment {

    @Autowired
    public CompanyTopLevelService companyTopLevelService;

    @Autowired
    public SourceCodeCache sourceCodeCache;

    @Autowired
    public SourceCodeRepository sourceCodeRepository;

    @Autowired
    public WorkbookRepository workbookRepository;

    @Autowired
    public ExecContextCache execContextCache;

    @Autowired
    public SourceCodeService sourceCodeService;

    @Autowired
    public SnippetCache snippetCache;

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

    public SourceCodeImpl plan = null;
    public Snippet s1 = null;
    public Snippet s2 = null;
    public Snippet s3 = null;
    public Snippet s4 = null;
    public Snippet s5 = null;
    public ExecContextImpl workbook = null;

    public WorkbookParamsYaml.WorkbookYaml workbookYaml;

    public Company company;

    public abstract String getPlanYamlAsString();

    public static String getPlanV8() {
        SourceCodeParamsYamlV1 planParamsYaml = new SourceCodeParamsYamlV1();
        planParamsYaml.source = new SourceCodeParamsYamlV1.SourceCodeV8();
        planParamsYaml.source.code = "SourceCode for experiment";
//            global: global-var
//            inline:
//              mh.hyper-params:
//                RNN: LSTM
//                batches: '40'
//                seed: '42'
//                time_steps: '7'

        planParamsYaml.source.variables = new SourceCodeParamsYamlV1.VariableDefinitionV8();
        planParamsYaml.source.variables.global = PreparingPlan.TEST_GLOBAL_VARIABLE;
        planParamsYaml.source.variables.inline.put(ConstsApi.MH_HYPER_PARAMS, Map.of("RNN", "LSTM", "batches", "40", "seed", "42", "time_steps", "7"));
        {
            SourceCodeParamsYamlV1.ProcessV8 p = new SourceCodeParamsYamlV1.ProcessV8();
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8("snippet-01:1.1");
            p.input.add( new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, PreparingPlan.TEST_GLOBAL_VARIABLE));
            p.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "assembled-raw-output"));
//      input:
//        - variable: test-variable
//          sourcing: launchpad
//      output:
//        - variable: assembled-raw-output
//          sourcing: launchpad

            planParamsYaml.source.processes.add(p);
        }
        {
            SourceCodeParamsYamlV1.ProcessV8 p = new SourceCodeParamsYamlV1.ProcessV8();
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8("snippet-02:1.1");
            p.input.add( new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "assembled-raw-output"));
            p.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));

            planParamsYaml.source.processes.add(p);

            p.subProcesses = new SourceCodeParamsYamlV1.SubProcessesV8();
            p.subProcesses.logic = EnumsApi.SourceCodeSubProcessLogic.and;

            SourceCodeParamsYamlV1.ProcessV8 p1 = new SourceCodeParamsYamlV1.ProcessV8();
            p1.name = "feature-processing-1";
            p1.code = "feature-processing-1";
            p1.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8("snippet-03:1.1");
            p1.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p1.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-output-1"));

            SourceCodeParamsYamlV1.ProcessV8 p2 = new SourceCodeParamsYamlV1.ProcessV8();
            p2.name = "feature-processing-2";
            p2.code = "feature-processing-2";
            p2.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8("snippet-04:1.1");
            p2.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p2.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-output-2"));

            SourceCodeParamsYamlV1.ProcessV8 p3 = new SourceCodeParamsYamlV1.ProcessV8();
            p3.name = "feature-processing-3";
            p3.code = "feature-processing-3";
            p3.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8("snippet-05:1.1");
            p3.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p3.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-output-3"));

            p.subProcesses.processes = List.of(p1, p2);
        }
        {
//    - code: mh.permute-variables-and-hyper-params
//      name: permute variables and hyper params
//      metas:
//        - key: variables
//          value: feature-processing_matrix_of_winning, feature-processing_cluster_size_1
//      snippets:
//        - code: mh.permute-variables-and-hyper-params
//      output:
//        - variable: feature-per-task
//          sourcing: launchpad

            SourceCodeParamsYamlV1.ProcessV8 p = new SourceCodeParamsYamlV1.ProcessV8();
            p.name = "permute variables and hyper params";
            p.code = "mh.permute-variables-and-hyper-params";

            p.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8("mh.permute-variables-and-hyper-params", EnumsApi.SnippetExecContext.internal);
            p.metas = List.of(new Meta("variables", "feature-output-1,feature-output-2,feature-output-3", null));
            p.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-per-task"));

            planParamsYaml.source.processes.add(p);

            p.subProcesses = new SourceCodeParamsYamlV1.SubProcessesV8();
            p.subProcesses.logic = EnumsApi.SourceCodeSubProcessLogic.sequential;

            SourceCodeParamsYamlV1.ProcessV8 p1 = new SourceCodeParamsYamlV1.ProcessV8();
            p1.name = "feature-processing-1";
            p1.code = "feature-processing-1";
            p1.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8(TEST_FIT_SNIPPET);
//            input:
//              - variable: feature-per-task
//                sourcing: launchpad
//            output:
//              - variable: model
//                sourcing: launchpad
            p1.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p1.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-per-task"));
            p1.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "model"));

            SourceCodeParamsYamlV1.ProcessV8 p2 = new SourceCodeParamsYamlV1.ProcessV8();
            p2.name = "feature-processing-2";
            p2.code = "feature-processing-2";
            p2.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8(TEST_PREDICT_SNIPPET);
            p2.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
//              - variable: metrics
//                sourcing: launchpad
//              - variable: predicted
//                sourcing: launchpad
            p2.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "metrics"));
            p2.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "predicted"));

            SourceCodeParamsYamlV1.ProcessV8 p3 = new SourceCodeParamsYamlV1.ProcessV8();
            p3.name = "feature-processing-3";
            p3.code = "feature-processing-3";
            p3.snippet = new SourceCodeParamsYamlV1.SnippetDefForPlanV8(TEST_FITTING_SNIPPET);
            p3.input.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "predicted"));
            p3.output.add(new SourceCodeParamsYamlV1.VariableV8(EnumsApi.DataSourcing.launchpad, "overfitting"));

            p.subProcesses.processes = List.of(p1, p2);
        }
        final SourceCodeParamsYamlUtilsV1 forVersion = (SourceCodeParamsYamlUtilsV1) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(8);
        String yaml = forVersion.toString(planParamsYaml);
        return yaml;
    }

    public String getPlanParamsYamlAsString_Simple() {
        return getPlanV8();
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

        s1 = createSnippet("snippet-01:1.1");
        s2 = createSnippet("snippet-02:1.1");
        s3 = createSnippet("snippet-03:1.1");
        s4 = createSnippet("snippet-04:1.1");
        s5 = createSnippet("snippet-05:1.1");

        plan = new SourceCodeImpl();
        plan.setCode("test-sourceCode-code");

        String params = getPlanYamlAsString();
        plan.setParams(params);
        plan.setCreatedOn(System.currentTimeMillis());
        plan.companyId = company.uniqueId;


        SourceCode tempSourceCode = sourceCodeRepository.findByUidAndCompanyId(plan.getCode(), company.uniqueId);
        if (tempSourceCode !=null) {
            sourceCodeCache.deleteById(tempSourceCode.getId());
        }
        sourceCodeCache.save(plan);

        byte[] bytes = "A resource for input pool".getBytes();

        try {
            globalVariableService.deleteByVariable(TEST_GLOBAL_VARIABLE);
        } catch (Throwable th) {
            log.error("error preparing variables", th);
        }

        globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-01.txt");
        globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-02.txt");
        globalVariableService.save(new ByteArrayInputStream(bytes), bytes.length, TEST_GLOBAL_VARIABLE,"file-03.txt");

        workbookYaml = new WorkbookParamsYaml.WorkbookYaml();
        workbookYaml.poolCodes.computeIfAbsent(Consts.MH_WORKBOOK_INPUT_VARIABLE, o-> new ArrayList<>()).add(TEST_GLOBAL_VARIABLE);
    }

    private Snippet createSnippet(String snippetCode) {
        SnippetConfigYaml sc = new SnippetConfigYaml();
        sc.code = snippetCode;
        sc.type = snippetCode + "-type";
        sc.file = null;
        sc.setEnv("env-"+snippetCode);
        sc.sourcing = EnumsApi.SnippetSourcing.station;

        sc.info.setSigned(false);
        sc.info.setLength(1000);
//  metas:
//  - key: mh.task-params-version
//    value: '3'
        sc.metas.add(new Meta(ConstsApi.META_MH_TASK_PARAMS_VERSION, "5", null));
        Snippet s = new Snippet();
        Long snippetId = snippetRepository.findIdByCode(snippetCode);
        if (snippetId!=null) {
            snippetCache.delete(snippetId);
        }
        s.setCode(snippetCode);
        s.setType(sc.type);
        s.setParams(SnippetConfigYamlUtils.BASE_YAML_UTILS.toString(sc));

        snippetCache.save(s);
        return s;
    }

    @After
    public void afterPreparingPlan() {
        if (plan!=null && plan.getId()!=null) {
            try {
                sourceCodeCache.deleteById(plan.getId());
            } catch (Throwable th) {
                log.error("Error while planCache.deleteById()", th);
            }
        }
        if (company!=null && company.id!=null) {
            try {
                companyRepository.deleteById(company.id);
            } catch (Throwable th) {
                log.error("Error while companyRepository.deleteById()", th);
            }
        }
        deleteSnippet(s1);
        deleteSnippet(s2);
        deleteSnippet(s3);
        deleteSnippet(s4);
        deleteSnippet(s5);
        if (workbook!=null) {
            try {
                workbookRepository.deleteById(workbook.getId());
            } catch (Throwable th) {
                log.error("Error while workbookRepository.deleteById()", th);
            }
            try {
                taskRepository.deleteByWorkbookId(workbook.getId());
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
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getPlanYamlAsString());
        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        EnumsApi.SourceCodeValidateStatus status = sourceCodeService.validate(plan);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status);

        TaskProducingResultComplex result = execContextService.createWorkbook(plan.getId(), workbookYaml);
        workbook = (ExecContextImpl)result.execContext;

        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.ExecContextState.NONE.code, workbook.getExecState());


        EnumsApi.SourceCodeProducingStatus producingStatus = execContextService.toProducing(workbook.id);
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, producingStatus);
        workbook = execContextCache.findById(this.workbook.id);
        assertNotNull(workbook);
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, workbook.getExecState());

        result = sourceCodeService.produceAllTasks(true, plan, this.workbook);
        experiment = experimentCache.findById(experiment.id);
        this.workbook = (ExecContextImpl)result.execContext;
        assertEquals(result.numberOfTasks, taskRepository.findAllTaskIdsByWorkbookId(workbook.id).size());
        assertEquals(result.numberOfTasks, execContextService.getCountUnfinishedTasks(workbook));


        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCED.code, this.workbook.getExecState());

        return result;
    }

    private void deleteSnippet(Snippet s) {
        if (s!=null) {
            try {
                snippetCache.delete(s);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }
}
