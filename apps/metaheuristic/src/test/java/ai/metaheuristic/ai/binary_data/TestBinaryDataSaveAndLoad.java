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

package ai.metaheuristic.ai.binary_data;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.internal_functions.batch_result_processor.BatchResultProcessorTxService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYaml;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 6/6/2019
 * Time: 3:14 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class TestBinaryDataSaveAndLoad {

    private static final String DATA_FILE_BIN = "data-file.bin";
    private static final String TRG_DATA_FILE_BIN = "trg-data-file.bin";
    private static final String TEST_VARIABLE = "test-variable";
    public static final String SYSTEM_PARAMS_V_2_YAML = "system/params-v2.yaml";

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

    @Autowired private VariableTxService variableService;
    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private VariableTxService variableTxService;

    private static final int ARRAY_SIZE = 1_000_000;
    private static final Random r = new Random();

    @BeforeEach
    public void before() {
        txSupportForTestingService.deleteVariableByName(TEST_VARIABLE);
    }

    @AfterEach
    public void after() {
        txSupportForTestingService.deleteVariableByName(TEST_VARIABLE);
    }

    @Test
    public void testSaveAndLoadToTempFile(@TempDir Path tempDir) throws IOException {
        storeAndVerify(tempDir, Files.createTempFile(tempDir, "variable-", CommonConsts.BIN_EXT));
    }

    @Test
    public void testSaveAndLoad(@TempDir Path tempDir) throws IOException {
        storeAndVerify(tempDir, tempDir.resolve(TRG_DATA_FILE_BIN));
    }

    private void storeAndVerify(Path tempDir, Path trgFile) throws IOException {
        byte[] bytes = new byte[ARRAY_SIZE];
        r.nextBytes(bytes);

        Path dataFile = tempDir.resolve(DATA_FILE_BIN);
        Files.write(dataFile, bytes);

        Variable variable;
        try (InputStream is = Files.newInputStream(dataFile)) {
            final long size = Files.size(dataFile);
            variable = variableTxService.createInitializedTx(is, size, TEST_VARIABLE, DATA_FILE_BIN, 1L, "1,2,3", EnumsApi.VariableType.binary);
        }
        assertNotNull(variable);
        assertNotNull(variable.id);

        variableService.storeToFileWithTx(variable.id, trgFile);

        assertTrue(FileUtils.contentEquals(dataFile.toFile(), trgFile.toFile()));
    }

    @Test
    public void testSaveAndLoad1(@TempDir Path tempDir) throws IOException {
        byte[] bytes = new byte[ARRAY_SIZE];
        r.nextBytes(bytes);

        Path dataFile = tempDir.resolve(DATA_FILE_BIN);
        Files.write(dataFile, bytes);

        Variable variable;
        try (InputStream is = Files.newInputStream(dataFile)) {
            final long size = Files.size(dataFile);
            variable = variableTxService.createInitializedTx(is, size, TEST_VARIABLE, DATA_FILE_BIN, 1L, "1,2,3", EnumsApi.VariableType.binary);
        }
        assertNotNull(variable);
        assertNotNull(variable.id);

        BatchItemMappingYaml bimy = new BatchItemMappingYaml();
        bimy.targetDir = tempDir.getFileName().toString();
        bimy.targetPath = tempDir;
        bimy.filenames.put(variable.id.toString(), SYSTEM_PARAMS_V_2_YAML);

        Variable v = variableRepository.findByIdAsSimple(variable.id);
        assertNotNull(v);

        final Path defaultPath = tempDir.resolve("default-path-for-variables");
        Function<Variable, Path> mappingFunc = (var) -> BatchResultProcessorTxService.resolvePathFromMapping(List.of(bimy), defaultPath, var);

        variableService.storeVariableToFileWithTx(mappingFunc, List.of(v));

        Path trgFile = tempDir.resolve(SYSTEM_PARAMS_V_2_YAML);
        assertTrue(FileUtils.contentEquals(dataFile.toFile(), trgFile.toFile()));
    }

}