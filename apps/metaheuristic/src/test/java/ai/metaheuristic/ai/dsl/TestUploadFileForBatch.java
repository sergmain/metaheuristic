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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
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
        SourceCodeParamsYamlV1 planParamsYaml = new SourceCodeParamsYamlV1();

        planParamsYaml.source = new SourceCodeParamsYamlV1.SourceCodeV1();
        planParamsYaml.source.uid = "SourceCode for testing uploading batch file";
        {
            SourceCodeParamsYamlV1.ProcessV1 p = new SourceCodeParamsYamlV1.ProcessV1();
            p.name = "Plocess mh.variable-splitter";
            p.code = "process-mh.variable-splitter";

            p.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1(Consts.MH_VARIABLE_SPLITTER_FUNCTION, EnumsApi.FunctionExecContext.internal);
            p.output.add( new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher,"batch-array"));

            planParamsYaml.source.processes.add(p);
        }

        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        System.out.println("TestUploadFileForBatch.getPlanYamlAsString yaml:\n" + yaml);
        return yaml;
    }


    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public ExecContextService execContextService;
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
            if (uploadingStatus.execContextId !=null) {
                try {
                    execContextCache.deleteById(uploadingStatus.execContextId);
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
        final DispatcherContext context = new DispatcherContext(a, company);


        SourceCodeApiData.SourceCodeResult sourceCodeResult = sourceCodeTopLevelService.validateSourceCode(plan.id, context);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, sourceCodeResult.status);
        plan = sourceCodeCache.findById(plan.id);
        assertTrue(plan.isValid());

        String planYamlAsString = getPlanYamlAsString();
        System.out.println("actual sourceCode yaml:\n" + planYamlAsString);
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(planYamlAsString);
        MockMultipartFile mockFile = new MockMultipartFile("random-name.txt", "file-for-batch-processing.xml", StandardCharsets.UTF_8.toString(), "content of file".getBytes());

        uploadingStatus = batchTopLevelService.batchUploadFromFile(mockFile, plan.getId(), context);
        assertFalse(uploadingStatus.getErrorMessagesAsStr(), uploadingStatus.isErrorMessages());
        assertNotNull(uploadingStatus.batchId);
        assertNotNull(uploadingStatus.execContextId);

    }


}
