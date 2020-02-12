/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dsl;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.batch.BatchCache;
import ai.metaheuristic.ai.launchpad.batch.BatchTopLevelService;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV8;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 1/26/2020
 * Time: 1:26 AM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestUploadFileForBatch extends PreparingPlan {

    @Override
    public String getPlanYamlAsString() {
        PlanParamsYamlV8 planParamsYaml = new PlanParamsYamlV8();

        planParamsYaml.plan = new PlanParamsYamlV8.PlanYamlV8();
        planParamsYaml.plan.code = "SourceCode for testing uploading batch file";
        {
            PlanParamsYamlV8.ProcessV8 p = new PlanParamsYamlV8.ProcessV8();
            p.name = "Plocess mh.resource-splitter";
            p.code = "process-mh.resource-splitter";

            p.snippet = new PlanParamsYamlV8.SnippetDefForPlanV8(Consts.MH_RESOURCE_SPLITTER_SNIPPET, EnumsApi.SnippetExecContext.internal);
            p.output.add( new PlanParamsYamlV8.VariableV8(EnumsApi.DataSourcing.launchpad,"batch-array"));

            planParamsYaml.plan.processes.add(p);
        }

        String yaml = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        System.out.println("TestUploadFileForBatch.getPlanYamlAsString yaml:\n" + yaml);
        return yaml;
    }


    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public WorkbookService workbookService;
    @Autowired
    private BatchTopLevelService batchTopLevelService;
    @Autowired
    private BatchCache batchCache;
    @Autowired
    private SourceCodeTopLevelService sourceCodeTopLevelService;

    private BatchData.UploadingStatus uploadingStatus = null;

    @After
    public void afterTestUploadFileForBatch() {
        if (uploadingStatus!=null) {
            if (uploadingStatus.batchId!=null) {
                try {
                    batchCache.deleteById(uploadingStatus.batchId);
                } catch (Throwable th) {
                    log.error("Error", th);
                }
            }
            if (uploadingStatus.workbookId!=null) {
                try {
                    workbookCache.deleteById(uploadingStatus.workbookId);
                } catch (Throwable th) {
                    log.error("Error", th);
                }
            }
        }
    }

    @Test
    public void testUploadFileForBatch() {
        log.info("Start TestUploadFileForBatch.testUploadFileForBatch()");

        Account a = new Account();
        // ID==1L for admin but in this case it doesn't matter
        a.id = 1L;
        a.username = "test-batch-processing";
        a.companyId = company.uniqueId;
        final LaunchpadContext context = new LaunchpadContext(a, company);


        PlanApiData.PlanResult planResult = sourceCodeTopLevelService.validatePlan(plan.id, context);
        assertEquals(EnumsApi.PlanValidateStatus.OK, planResult.status);
        plan = sourceCodeCache.findById(plan.id);
        assertTrue(plan.isValid());

        String planYamlAsString = getPlanYamlAsString();
        System.out.println("actual sourceCode yaml:\n" + planYamlAsString);
        PlanParamsYaml planParamsYaml = PlanParamsYamlUtils.BASE_YAML_UTILS.to(planYamlAsString);
        MockMultipartFile mockFile = new MockMultipartFile("random-name.txt", "file-for-batch-processing.xml", StandardCharsets.UTF_8.toString(), "content of file".getBytes());

        uploadingStatus = batchTopLevelService.batchUploadFromFile(mockFile, plan.getId(), context);
        assertFalse(uploadingStatus.getErrorMessagesAsStr(), uploadingStatus.isErrorMessages());
        assertNotNull(uploadingStatus.batchId);
        assertNotNull(uploadingStatus.workbookId);

    }


}
