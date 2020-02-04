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
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.company.CompanyTopLevelService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookGraphTopLevelService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.plan.TaskCollector;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtilsV8;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV8;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
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

import static ai.metaheuristic.api.data.plan.PlanApiData.TaskProducingResultComplex;
import static org.junit.Assert.*;

@Slf4j
public abstract class PreparingPlan extends PreparingExperiment {

    @Autowired
    public CompanyTopLevelService companyTopLevelService;

    @Autowired
    public PlanCache planCache;

    @Autowired
    public PlanRepository planRepository;

    @Autowired
    public WorkbookRepository workbookRepository;

    @Autowired
    public WorkbookCache workbookCache;

    @Autowired
    public PlanService planService;

    @Autowired
    public SnippetCache snippetCache;

    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public WorkbookService workbookService;

    @Autowired
    public TaskPersistencer taskPersistencer;

    @Autowired
    public CompanyRepository companyRepository;

    @Autowired
    public WorkbookGraphTopLevelService workbookGraphTopLevelService;

    public PlanImpl plan = null;
    public Snippet s1 = null;
    public Snippet s2 = null;
    public Snippet s3 = null;
    public Snippet s4 = null;
    public Snippet s5 = null;
    public WorkbookImpl workbook = null;

    public WorkbookParamsYaml.WorkbookYaml workbookYaml;

    public Company company;

    public abstract String getPlanYamlAsString();

    public static String getPlanV8() {
        PlanParamsYamlV8 planParamsYaml = new PlanParamsYamlV8();
        planParamsYaml.plan = new PlanParamsYamlV8.PlanYamlV8();
        planParamsYaml.plan.code = "Plan for experiment";
//            global: global-var
//            inline:
//              mh.hyper-params:
//                RNN: LSTM
//                batches: '40'
//                seed: '42'
//                time_steps: '7'

        planParamsYaml.plan.variables = new PlanParamsYamlV8.VariableDefinitionV8();
        planParamsYaml.plan.variables.global = PreparingPlan.TEST_GLOBAL_VARIABLE;
        planParamsYaml.plan.variables.inline.put(ConstsApi.MH_HYPER_PARAMS, Map.of("RNN", "LSTM", "batches", "40", "seed", "42", "time_steps", "7"));
        {
            PlanParamsYamlV8.ProcessV8 p = new PlanParamsYamlV8.ProcessV8();
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8("snippet-01:1.1");
            p.input.add( new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, PreparingPlan.TEST_GLOBAL_VARIABLE));
            p.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "assembled-raw-output"));
//      input:
//        - variable: test-variable
//          sourcing: launchpad
//      output:
//        - variable: assembled-raw-output
//          sourcing: launchpad

            planParamsYaml.plan.processes.add(p);
        }
        {
            PlanParamsYamlV8.ProcessV8 p = new PlanParamsYamlV8.ProcessV8();
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8("snippet-02:1.1");
            p.input.add( new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "assembled-raw-output"));
            p.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));

            planParamsYaml.plan.processes.add(p);

            p.subProcesses = new PlanParamsYamlV8.SubProcessesV8();
            p.subProcesses.exec = EnumsApi.PlanProcessExec.parallel;

            PlanParamsYamlV8.ProcessV8 p1 = new PlanParamsYamlV8.ProcessV8();
            p1.name = "feature-processing-1";
            p1.code = "feature-processing-1";
            p1.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8("snippet-03:1.1");
            p1.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p1.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-output-1"));

            PlanParamsYamlV8.ProcessV8 p2 = new PlanParamsYamlV8.ProcessV8();
            p2.name = "feature-processing-2";
            p2.code = "feature-processing-2";
            p2.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8("snippet-04:1.1");
            p2.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p2.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-output-2"));

            PlanParamsYamlV8.ProcessV8 p3 = new PlanParamsYamlV8.ProcessV8();
            p3.name = "feature-processing-3";
            p3.code = "feature-processing-3";
            p3.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8("snippet-05:1.1");
            p3.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p3.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-output-3"));

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

            PlanParamsYamlV8.ProcessV8 p = new PlanParamsYamlV8.ProcessV8();
            p.name = "permute variables and hyper params";
            p.code = "mh.permute-variables-and-hyper-params";

            p.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8("mh.permute-variables-and-hyper-params", EnumsApi.SnippetExecContext.internal);
            p.metas = List.of(new Meta("variables", "feature-output-1,feature-output-2,feature-output-3", null));
            p.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-per-task"));

            planParamsYaml.plan.processes.add(p);

            p.subProcesses = new PlanParamsYamlV8.SubProcessesV8();
            p.subProcesses.exec = EnumsApi.PlanProcessExec.sequential;

            PlanParamsYamlV8.ProcessV8 p1 = new PlanParamsYamlV8.ProcessV8();
            p1.name = "feature-processing-1";
            p1.code = "feature-processing-1";
            p1.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8(TEST_FIT_SNIPPET);
//            input:
//              - variable: feature-per-task
//                sourcing: launchpad
//            output:
//              - variable: model
//                sourcing: launchpad
            p1.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
            p1.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "feature-per-task"));
            p1.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "model"));

            PlanParamsYamlV8.ProcessV8 p2 = new PlanParamsYamlV8.ProcessV8();
            p2.name = "feature-processing-2";
            p2.code = "feature-processing-2";
            p2.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8(TEST_PREDICT_SNIPPET);
            p2.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "dataset-processing-output"));
//              - variable: metrics
//                sourcing: launchpad
//              - variable: predicted
//                sourcing: launchpad
            p2.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "metrics"));
            p2.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "predicted"));

            PlanParamsYamlV8.ProcessV8 p3 = new PlanParamsYamlV8.ProcessV8();
            p3.name = "feature-processing-3";
            p3.code = "feature-processing-3";
            p3.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8(TEST_FITTING_SNIPPET);
            p3.input.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "predicted"));
            p3.output.add(new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad, "overfitting"));

            p.subProcesses.processes = List.of(p1, p2);
        }
        final PlanParamsYamlUtilsV8 forVersion = (PlanParamsYamlUtilsV8) PlanParamsYamlUtils.BASE_YAML_UTILS.getForVersion(8);
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

        plan = new PlanImpl();
        plan.setCode("test-plan-code");

        String params = getPlanYamlAsString();
        plan.setParams(params);
        plan.setCreatedOn(System.currentTimeMillis());
        plan.companyId = company.uniqueId;


        Plan tempPlan = planRepository.findByCodeAndCompanyId(plan.getCode(), company.uniqueId);
        if (tempPlan!=null) {
            planCache.deleteById(tempPlan.getId());
        }
        planCache.save(plan);

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
                planCache.deleteById(plan.getId());
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
        PlanParamsYaml planParamsYaml = PlanParamsYamlUtils.BASE_YAML_UTILS.to(getPlanYamlAsString());
        assertFalse(planParamsYaml.plan.processes.isEmpty());

        EnumsApi.PlanValidateStatus status = planService.validate(plan);
        assertEquals(EnumsApi.PlanValidateStatus.OK, status);

        TaskProducingResultComplex result = workbookService.createWorkbook(plan.getId(), workbookYaml);
        workbook = (WorkbookImpl)result.workbook;

        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.NONE.code, workbook.getExecState());


        EnumsApi.PlanProducingStatus producingStatus = workbookService.toProducing(workbook.id);
        assertEquals(EnumsApi.PlanProducingStatus.OK, producingStatus);
        workbook = workbookCache.findById(this.workbook.id);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCING.code, workbook.getExecState());

        result = planService.produceAllTasks(true, plan, this.workbook);
        experiment = experimentCache.findById(experiment.id);
        this.workbook = (WorkbookImpl)result.workbook;
        assertEquals(result.numberOfTasks, taskRepository.findAllTaskIdsByWorkbookId(workbook.id).size());
        assertEquals(result.numberOfTasks, workbookService.getCountUnfinishedTasks(workbook));


        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCED.code, this.workbook.getExecState());

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
