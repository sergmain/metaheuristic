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
package ai.metaheuristic.ai.service;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
@Slf4j
public class TestFeatureWithAllError extends FeatureMethods {

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
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;

    @Test
    public void testFeatureCompletionWithAllError() {
        createExperiment();

        System.out.println("start step_0_0_produce_tasks_and_start()");
        step_0_0_produce_tasks_and_start();

        PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(processorIdAndCoreIds, preparingSourceCodeData, preparingCodeData);

        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        long mills = System.currentTimeMillis();
        log.info("Start getTaskAndAssignToProcessor_mustBeNewTask()");
        DispatcherCommParamsYaml.AssignedTask simpleTask = getTaskAndAssignToProcessor_mustBeNewTask(processorIdAndCoreIds);
        log.info("getTaskAndAssignToProcessor_mustBeNewTask() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        DispatcherCommParamsYaml.AssignedTask task = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        // there isn't a new task for processing
        // we will get the same task
        assertNotNull(task);
        assertEquals(simpleTask.taskId, task.taskId);

        mills = System.currentTimeMillis();
        log.info("Start storeConsoleResultAsError()");
        storeConsoleResultAsError(processorIdAndCoreIds);
        log.info("storeConsoleResultAsError() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        mills = System.currentTimeMillis();
        log.info("Start noNewTask()");

        DispatcherCommParamsYaml.AssignedTask task2 = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        assertNull(task2);

        log.info("noNewTask() was finished for {} milliseconds", System.currentTimeMillis() - mills);

    }
}
