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
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.plan.TaskCollector;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtilsV2;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV2;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.process.ProcessV2;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ai.metaheuristic.api.data.plan.PlanApiData.TaskProducingResultComplex;
import static org.junit.Assert.*;

@Slf4j
public abstract class PreparingPlan extends PreparingExperiment {

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

    public PlanImpl plan = null;
    public PlanParamsYamlV2 planParamsYaml = null;
    public Snippet s1 = null;
    public Snippet s2 = null;
    public Snippet s3 = null;
    public Snippet s4 = null;
    public Snippet s5 = null;
    public WorkbookImpl workbook = null;

    public WorkbookParamsYaml workbookParamsYaml;

    public Company company;

    public abstract String getPlanYamlAsString();

    public String getPlanParamsYamlAsString_Simple() {
        planParamsYaml = new PlanParamsYamlV2();
        planParamsYaml.planYaml = new PlanParamsYamlV2.PlanYamlV2();
        {
            ProcessV2 p = new ProcessV2();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippetCodes = List.of("snippet-01:1.1");
            p.collectResources = false;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "assembled-raw-output";
            p.outputType = "assembled-raw-output";

            planParamsYaml.planYaml.processes.add(p);
        }
        {
            ProcessV2 p = new ProcessV2();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = List.of("snippet-02:1.1");
            p.collectResources = true;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "dataset-processing-output";
            p.outputType = "dataset-processing-output";

            planParamsYaml.planYaml.processes.add(p);
        }
        {
            ProcessV2 p = new ProcessV2();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = List.of("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = true;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "feature-output";
            p.outputType = "feature-output";

            planParamsYaml.planYaml.processes.add(p);
        }
        {
            ProcessV2 p = new ProcessV2();
            p.type = EnumsApi.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = PreparingExperiment.TEST_EXPERIMENT_CODE_01;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.metas.addAll(
                    Arrays.asList(
                            new Meta("assembled-raw", "assembled-raw-output", null),
                            new Meta("dataset", "dataset-processing-output", null),
                            new Meta("feature", "feature-output", null)
                    )
            );

            planParamsYaml.planYaml.processes.add(p);
        }
//        planYaml.planCode = "test-plan-code";

//        planParamsYaml = new PlanParamsYamlV2();
//        planParamsYaml.planYaml = planYaml;

        final PlanParamsYamlUtilsV2 forVersion = (PlanParamsYamlUtilsV2) PlanParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
        String yaml = forVersion.toString(planParamsYaml);
//        String yaml = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        System.out.println(yaml);
        return yaml;
    }

    @Autowired
    private BinaryDataService binaryDataService;

    public static final String INPUT_POOL_CODE = "test-input-pool-code";
    public static final String INPUT_RESOURCE_CODE = "test-input-resource-code-";

    @Before
    public void beforePreparingPlan() {
        assertTrue(globals.isUnitTesting);

        company = new Company();
        company.name = "Test company #2";
        companyRepository.save(company);
        assertNotNull(company.id);

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
        plan.companyId = company.id;


        Plan tempPlan = planRepository.findByCode(plan.getCode());
        if (tempPlan!=null) {
            planCache.deleteById(tempPlan.getId());
        }
        planCache.save(plan);

        byte[] bytes = "A resource for input pool".getBytes();

        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                EnumsApi.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-01.txt",
                null, null);
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                EnumsApi.BinaryDataType.DATA,INPUT_RESOURCE_CODE+2, INPUT_POOL_CODE,
                true, "file-02.txt",
                null, null);
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                EnumsApi.BinaryDataType.DATA,INPUT_RESOURCE_CODE+3, INPUT_POOL_CODE,
                true, "file-03.txt",
                null, null);

        workbookParamsYaml = new WorkbookParamsYaml();
        workbookParamsYaml.workbookYaml.poolCodes.computeIfAbsent(Consts.WORKBOOK_INPUT_TYPE, o-> new ArrayList<>()).add(INPUT_POOL_CODE);
    }

    private Snippet createSnippet(String snippetCode) {
        SnippetApiData.SnippetConfig sc = new SnippetApiData.SnippetConfig();
        sc.code = snippetCode;
        sc.type = snippetCode + "-type";
        sc.file = null;
        sc.setEnv("env-"+snippetCode);
        sc.sourcing = EnumsApi.SnippetSourcing.station;
        sc.metrics = false;

        sc.info.setSigned(false);
        sc.info.setLength(1000);

        Snippet s = new Snippet();
        Long snippetId = snippetRepository.findIdByCode(snippetCode);
        if (snippetId!=null) {
            snippetCache.delete(snippetId);
        }
        s.setCode(snippetCode);
        s.setType(sc.type);
        s.setParams(SnippetConfigUtils.toString(sc));

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
            binaryDataService.deleteByPoolCodeAndDataType(INPUT_POOL_CODE, EnumsApi.BinaryDataType.DATA);
        } catch (Throwable th) {
            log.error("error", th);
        }
    }

    public TaskProducingResultComplex produceTasksForTest() {
        assertFalse(planParamsYaml.planYaml.processes.isEmpty());
        assertEquals(EnumsApi.ProcessType.EXPERIMENT, planParamsYaml.planYaml.processes.get(planParamsYaml.planYaml.processes.size()-1).type);

        EnumsApi.PlanValidateStatus status = planService.validate(plan);
        assertEquals(EnumsApi.PlanValidateStatus.OK, status);

        TaskProducingResultComplex result = workbookService.createWorkbook(plan.getId(), workbookParamsYaml);
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
