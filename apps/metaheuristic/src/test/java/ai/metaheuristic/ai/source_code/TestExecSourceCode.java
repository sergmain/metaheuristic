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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.Nullable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/11/2021
 * Time: 3:19 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestExecSourceCode extends PreparingSourceCode {

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testOk() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        SourceCodeApiData.SourceCodeResult scrSub = null;
        try {
            scrSub = sourceCodeTopLevelService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-correct-sub-source-code.yaml"), company.uniqueId);
            assertTrue(scrSub.isValid());
            assertFalse(scrSub.isErrorMessages());
            System.out.println(scrSub.getErrorMessagesAsStr());
            assertEquals(scrSub.validationResult.status, EnumsApi.SourceCodeValidateStatus.OK);

            scr = sourceCodeTopLevelService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-correct.yaml"), company.uniqueId);
            assertTrue(scr.isValid());
            assertFalse(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.OK);
        } finally {
            finalize(scrSub);
            finalize(scr);
        }
    }

    @Test
    public void testRecursionExecError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeTopLevelService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-1.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());

            assertNotEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR);
            assertNotEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.META_NOT_FOUND_ERROR);
            assertNotEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR);
            assertEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_RECURSION_ERROR);
        } finally {
            finalize(scr);
        }
    }

    @Test
    public void testMetaNotFoundError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeTopLevelService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-2.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertNotEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR);
            assertEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.META_NOT_FOUND_ERROR);
        } finally {
            finalize(scr);
        }
    }

    @Test
    public void testInputsCountMismatchError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeTopLevelService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-3.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertNotEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR);
            assertEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.INPUT_VARIABLES_COUNT_MISMATCH_ERROR);
        } finally {
            finalize(scr);
        }
    }

    @Test
    public void testOutputssCountMismatchError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeTopLevelService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-4.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertNotEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR);
            assertEquals(scr.validationResult.status, EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLES_COUNT_MISMATCH_ERROR);
        } finally {
            finalize(scr);
        }
    }

    private void finalize(@Nullable SourceCodeApiData.SourceCodeResult scr) {
        if (scr != null) {
            SourceCodeImpl sc = Objects.requireNonNull(sourceCodeCache.findById(scr.id));
            try {
                sourceCodeService.deleteSourceCodeById(sc.id);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    private String getParams(String name) throws IOException {
        String params = IOUtils.resourceToString(name, StandardCharsets.UTF_8);

        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(params);
        sourceCodeParamsYaml.checkIntegrity();

        cleanUp(sourceCodeParamsYaml.source.uid);
        return params;
    }
}
