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

package ai.metaheuristic.ai.dsl;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 1/26/2020
 * Time: 1:26 AM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext
@AutoConfigureCache
public class TestUploadFileForBatch extends PreparingSourceCode {

    @Override
    public String getSourceCodeYamlAsString() {
        SourceCodeParamsYamlV1 planParamsYaml = new SourceCodeParamsYamlV1();

        planParamsYaml.source = new SourceCodeParamsYamlV1.SourceCodeV1();
        planParamsYaml.source.uid = "SourceCode for testing uploading batch file";
        planParamsYaml.source.variables.startInputAs = "batch-data";
        {
            SourceCodeParamsYamlV1.ProcessV1 p = new SourceCodeParamsYamlV1.ProcessV1();
            p.name = "Process mh.variable-splitter";
            p.code = "process-mh.variable-splitter";

            p.function = new SourceCodeParamsYamlV1.FunctionDefForSourceCodeV1(Consts.MH_BATCH_SPLITTER_FUNCTION, EnumsApi.FunctionExecContext.internal);
            p.outputs.add( new SourceCodeParamsYamlV1.VariableV1(EnumsApi.DataSourcing.dispatcher,"batch-array"));

            planParamsYaml.source.processes.add(p);
        }

        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        System.out.println("TestUploadFileForBatch.getPlanYamlAsString yaml:\n" + yaml);
        return yaml;
    }


    @Autowired
    public TaskService taskService;
    @Autowired
    public ExecContextService execContextService;
    @Autowired
    private BatchTopLevelService batchTopLevelService;

    @Autowired
    private SourceCodeTopLevelService sourceCodeTopLevelService;

    private BatchData.UploadingStatus uploadingStatus = null;

    @AfterEach
    public void afterTestUploadFileForBatch() {
        if (uploadingStatus!=null) {
            if (uploadingStatus.batchId!=null) {
                try {
                    txSupportForTestingService.batchCacheDeleteById(uploadingStatus.batchId);
                } catch (Throwable th) {
                    log.error("Error", th);
                }
            }
            if (uploadingStatus.execContextId !=null) {
                try {
                    taskRepository.deleteByExecContextId(uploadingStatus.execContextId);
                } catch (Throwable th) {
                    log.error("Error", th);
                }
            }
            if (uploadingStatus.execContextId !=null) {
                try {
                    txSupportForTestingService.execContextCacheDeleteById(uploadingStatus.execContextId);
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


        SourceCodeApiData.SourceCodeResult sourceCodeResult = sourceCodeService.validateSourceCode(sourceCode.id, context);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, sourceCodeResult.validationResult.status);
        sourceCode = Objects.requireNonNull(sourceCodeCache.findById(sourceCode.id));
        assertNotNull(sourceCode);
        assertTrue(sourceCode.isValid());

        String planYamlAsString = getSourceCodeYamlAsString();
        System.out.println("actual sourceCode yaml:\n" + planYamlAsString);
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(planYamlAsString);
        MockMultipartFile mockFile = new MockMultipartFile("random-name.txt", "file-for-batch-processing.xml", StandardCharsets.UTF_8.toString(), "content of file".getBytes());

        uploadingStatus = batchTopLevelService.batchUploadFromFile(mockFile, sourceCode.getId(), context);
        assertFalse(uploadingStatus.isErrorMessages(), uploadingStatus.getErrorMessagesAsStr());
        assertNotNull(uploadingStatus.batchId);
        assertNotNull(uploadingStatus.execContextId);

    }


}
