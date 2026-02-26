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

package ai.metaheuristic.ai.internal_function.permute_variables;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
class TestPermuteValuesOfVariables extends PreparingSourceCode {

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

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private ExecContextTxService execContextTxService;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextTaskStateService execContextTaskStateTopLevelService;
    @Autowired private ExecContextGraphTopLevelService execContextGraphTopLevelService;
    @Autowired private ExecContextRepository execContextRepository;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @SneakyThrows
    @Override
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/variables/permute-values-of-variables.yaml", StandardCharsets.UTF_8);
    }

    @SuppressWarnings("ConstantValue")
    @AfterEach
    public void afterTestSourceCodeService() {
        System.out.println("Finished TestSourceCodeService.afterTestSourceCodeService()");
        if (getExecContextForTest() !=null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void testCreateTasks() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        // ======================

        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        System.out.println("start findTaskForRegisteringInQueue()");
        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);

        final List<Long> taskIds = getFinishedTaskVertices(getExecContextForTest());
        assertEquals(2, taskIds.size());

        System.out.println("start findTaskForRegisteringInQueue() #5");

        // mh.permute-variables
        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);

        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId,
            ()->execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(getExecContextForTest().id, getExecContextForTest().execContextTaskStateId));

        // mh.permute-variables
        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);

        // mh.finish
        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);

        finalAssertions(5);
    }

}
