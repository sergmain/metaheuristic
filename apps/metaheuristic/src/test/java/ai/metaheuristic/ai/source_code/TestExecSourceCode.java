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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTxService;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/11/2021
 * Time: 3:19 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
class TestExecSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private SourceCodeService sourceCodeService;
    @Autowired private SourceCodeCache sourceCodeCache;
    @Autowired private SourceCodeTxService sourceCodeTxService;
    @Autowired private CompanyTopLevelService companyTopLevelService;
    @Autowired private CompanyRepository companyRepository;

    private @Nullable Company company = null;

    @BeforeEach
    public void init() {
        company = new Company();
        company.name = "Test company #2";
        companyTopLevelService.addCompany(company);
        company = Objects.requireNonNull(companyTopLevelService.getCompanyByUniqueId(company.uniqueId));

        assertNotNull(company.id);
        assertNotNull(company.uniqueId);

        // id==1L must be assigned only to master company
        assertNotEquals(Consts.ID_1, company.id);
    }

    @AfterEach
    public void after() {
        if (company!=null && company.id!=null) {
            companyRepository.deleteById(company.id);
        }
    }

    @Test
    public void testOk() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        SourceCodeApiData.SourceCodeResult scrSub = null;
        try {
            scrSub = sourceCodeService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-correct-sub-source-code.yaml"), company.uniqueId);
            assertTrue(scrSub.isValid());
            assertFalse(scrSub.isErrorMessages());
            System.out.println(scrSub.getErrorMessagesAsStr());
            assertEquals(scrSub.validationResult.status, EnumsApi.SourceCodeValidateStatus.OK);

            scr = sourceCodeService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-correct.yaml"), company.uniqueId);
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
            scr = sourceCodeService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-1.yaml"), company.uniqueId);
            assertTrue(scr.isValid());
        } finally {
            finalize(scr);
        }
    }

    @Test
    public void testMetaNotFoundError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-2.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertNotEquals(EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR, scr.validationResult.status);
            assertEquals(EnumsApi.SourceCodeValidateStatus.META_NOT_FOUND_ERROR, scr.validationResult.status, scr.validationResult.error);
        } finally {
            finalize(scr);
        }
    }

    @Test
    public void testInputsCountMismatchError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-3.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertNotEquals(EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR, scr.validationResult.status);
            assertEquals(EnumsApi.SourceCodeValidateStatus.SOURCE_OF_VARIABLE_NOT_FOUND_ERROR, scr.validationResult.status, scr.validationResult.error);
        } finally {
            finalize(scr);
        }
    }

    @Test
    public void testOutputsCountMismatchError() throws IOException {
        SourceCodeApiData.SourceCodeResult scr = null;
        try {
            scr = sourceCodeService.createSourceCode(getParams("/source_code/yaml/for-testing-exec-source-code-4.yaml"), company.uniqueId);
            assertFalse(scr.isValid());
            assertTrue(scr.isErrorMessages());
            System.out.println(scr.getErrorMessagesAsStr());
            assertNotEquals(EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR, scr.validationResult.status);
            assertEquals(EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLES_COUNT_MISMATCH_ERROR, scr.validationResult.status);
        } finally {
            finalize(scr);
        }
    }

    private void finalize(SourceCodeApiData.@Nullable SourceCodeResult scr) {
        if (scr != null) {
            SourceCodeImpl sc = Objects.requireNonNull(sourceCodeCache.findById(scr.id));
            try {
                sourceCodeTxService.deleteSourceCodeById(sc.id);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    private String getParams(String name) throws IOException {
        String params = IOUtils.resourceToString(name, StandardCharsets.UTF_8);

        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(params);
        sourceCodeParamsYaml.checkIntegrity();

        preparingSourceCodeService.cleanUp(sourceCodeParamsYaml.source.uid);
        return params;
    }
}
