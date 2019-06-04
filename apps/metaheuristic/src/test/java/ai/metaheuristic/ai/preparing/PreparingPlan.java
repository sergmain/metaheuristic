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
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.plan.TaskCollector;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import ai.metaheuristic.api.v1.data.Meta;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.Process;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.yaml.plan.PlanYamlUtils;
import ai.metaheuristic.api.v1.data.InputResourceParam;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

@Slf4j
public abstract class PreparingPlan extends PreparingExperiment {

    @Autowired
    public PlanCache planCache;

    @Autowired
    public PlanRepository planRepository;

    @Autowired
    public WorkbookRepository workbookRepository;

    @Autowired
    public PlanService planService;

    @Autowired
    public SnippetCache snippetCache;

    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public TaskPersistencer taskPersistencer;

    public PlanImpl plan = null;
    public PlanApiData.PlanYaml planYaml = null;
    public Snippet s1 = null;
    public Snippet s2 = null;
    public Snippet s3 = null;
    public Snippet s4 = null;
    public Snippet s5 = null;
    public Workbook workbook = null;

    public InputResourceParam inputResourceParam;


    public abstract String getPlanYamlAsString();

    public String getPlanYamlAsString_Simple() {
        planYaml = new PlanApiData.PlanYaml();
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = false;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "assembled-raw-output";

            planYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "dataset-processing-output";

            planYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = true;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "feature-output";

            planYaml.processes.add(p);
        }
        {
            Process p = new Process();
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

            planYaml.processes.add(p);
        }

        String yaml = PlanYamlUtils.toString(planYaml);
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

        inputResourceParam = new InputResourceParam();
        inputResourceParam.poolCodes = new HashMap<>();
        inputResourceParam.poolCodes.computeIfAbsent(Consts.WORKBOOK_INPUT_TYPE, o-> new ArrayList<>()).add(INPUT_POOL_CODE);
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
        Snippet sn = snippetRepository.findByCode(snippetCode);
        if (sn!=null) {
            snippetCache.delete(sn.getId());
        }
        s.setCode(snippetCode);
        s.setType(sc.type);
        s.setParams(SnippetConfigUtils.toString(sc));

        snippetCache.save(s);
        return s;
    }

    @After
    public void afterPreparingPlan() {
        if (plan!=null) {
            try {
                planCache.deleteById(plan.getId());
            } catch (Throwable th) {
                log.error("Error while planCache.deleteById()", th);
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

    public PlanApiData.TaskProducingResultComplex produceTasksForTest() {
        assertFalse(planYaml.processes.isEmpty());
        assertEquals(EnumsApi.ProcessType.EXPERIMENT, planYaml.processes.get(planYaml.processes.size()-1).type);

        EnumsApi.PlanValidateStatus status = planService.validate(plan);
        assertEquals(EnumsApi.PlanValidateStatus.OK, status);

        PlanApiData.TaskProducingResultComplex result = planService.createWorkbook(plan.getId(), InputResourceParamUtils.toString(inputResourceParam));
        workbook = result.workbook;

        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.NONE.code, workbook.getExecState());


        EnumsApi.PlanProducingStatus producingStatus = planService.toProducing(workbook);
        assertEquals(EnumsApi.PlanProducingStatus.OK, producingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCING.code, workbook.getExecState());

        result = planService.produceAllTasks(true, plan, workbook);
        workbook = result.workbook;
        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCED.code, workbook.getExecState());

        experiment = experimentCache.findById(experiment.getId());
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
