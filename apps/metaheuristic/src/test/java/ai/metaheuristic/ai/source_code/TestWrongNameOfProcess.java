/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 3/14/2020
 * Time: 8:53 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestWrongNameOfProcess extends PreparingSourceCode {

    @Autowired private SourceCodeValidationService sourceCodeValidationService;
    @Autowired private SourceCodeService sourceCodeService;

    @Override
    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/for-testing-wrong-name-of-process.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    // The YAML used here describes a user process whose `code` is `mh.finish` (the implicit final
    // process name) but whose `function.code` is `mh.nop` — i.e. someone mis-named a regular
    // process as the implicit terminator. The expected behavior is that createSourceCode rejects
    // this with WRONG_CODE_OF_PROCESS_ERROR via static (YAML-only) validation before the
    // graph-building stage. We bypass the standard PreparingSourceCode setup because that setup
    // requires the SourceCode to be persisted, which is precisely what should NOT happen for an
    // invalid YAML.

    @Override
    public void beforePreparingSourceCode() {
        // intentionally no-op: the YAML under test is invalid by design and must not be persisted.
    }

    @Override
    public void afterPreparingSourceCode() {
        // intentionally no-op: nothing was persisted.
    }

    @Test
    public void test() {
        String yaml = resolveSourceCode(getSourceCodeAndLang());

        // companyUniqueId=2L; createSourceCode must reject the YAML before reaching persistence,
        // so the company value is irrelevant — any non-1L value is fine.
        SourceCodeApiData.SourceCodeResult result = sourceCodeService.createSourceCode(
                yaml, EnumsApi.SourceCodeLang.yaml, 2L);

        // Red/Green-3: desired behavior. Static (YAML-only) validation must run BEFORE the graph
        // builder, so the YAML is rejected cleanly with WRONG_CODE_OF_PROCESS_ERROR. No persistence,
        // no JGraphT exception leaking out.
        assertNull(result.id, "id should be null because nothing was persisted");
        assertTrue(result.isErrorMessages(), "result should carry an error message");
        assertEquals(EnumsApi.SourceCodeValidateStatus.WRONG_CODE_OF_PROCESS_ERROR, result.validationResult.status,
                "static validation must reject mh.finish-coded process with non-mh.finish function");
        String firstError = result.getErrorMessagesAsList().get(0);
        assertTrue(firstError.contains("177.215"),
                "error message should reference validation code 177.215, actual: " + firstError);
    }
}
