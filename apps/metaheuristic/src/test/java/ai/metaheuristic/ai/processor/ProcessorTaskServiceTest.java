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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.sec.AdditionalCustomUserDetails;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.data.FunctionApiData;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 6/26/2023
 * Time: 8:27 PM
 */
@Execution(CONCURRENT)
class ProcessorTaskServiceTest {

    @Test
    public void test_actualSave(@TempDir Path temp) {
        ProcessorCoreTask task = new ProcessorCoreTask();
        Path taskDir = temp;

        Path taskYaml = temp.resolve("taskYaml.yaml");
        assertDoesNotThrow(()->ProcessorTaskService.actualSave(task, taskDir, taskYaml));
    }

    @Test
    public void test_deleteTaskAssetDir_deletesAssetDirOnly(@TempDir Path temp) throws Exception {
        Path assetDir = Files.createDirectories(temp.resolve(ConstsApi.ASSET_DIR).resolve("nested"));
        Files.writeString(assetDir.resolve("asset.txt"), "asset");
        Path artifactDir = Files.createDirectories(temp.resolve(ConstsApi.ARTIFACTS_DIR));
        Files.writeString(artifactDir.resolve("artifact.txt"), "artifact");

        assertTrue(ProcessorTaskService.deleteTaskAssetDir(temp));

        assertTrue(Files.notExists(temp.resolve(ConstsApi.ASSET_DIR)));
        assertTrue(Files.exists(artifactDir.resolve("artifact.txt")), "only the 'asset' sub-dir must be deleted");
        assertTrue(Files.exists(temp));
    }

    @Test
    public void test_deleteTaskAssetDir_noAssetDir(@TempDir Path temp) throws Exception {
        Files.createDirectories(temp.resolve(ConstsApi.ARTIFACTS_DIR));

        assertFalse(ProcessorTaskService.deleteTaskAssetDir(temp));

        assertTrue(Files.exists(temp.resolve(ConstsApi.ARTIFACTS_DIR)));
    }

    @Test
    public void test_deleteTaskAssetDir_isIdempotent(@TempDir Path temp) throws Exception {
        Files.createDirectories(temp.resolve(ConstsApi.ASSET_DIR));

        assertTrue(ProcessorTaskService.deleteTaskAssetDir(temp));
        assertFalse(ProcessorTaskService.deleteTaskAssetDir(temp));
    }

    // ---- startup scan of task dirs: the log must list only tasks which will be re-run ----
    // Every @Test uses its own taskIds - the logger is shared by @Test methods running concurrently.

    private record StartedProcessor(ProcessorTaskService service, ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {}

    private record Restart(StartedProcessor processor, List<String> messages) {}

    /**
     * Starts a real standalone processor over processorPath without Spring: real Globals, real ProcessorEnvironment
     * (built-in standalone env.yaml and dispatcher lookup), real metadata.yaml in processorPath.
     * Every call is a processor (re)start - ProcessorTaskService.postConstruct() scans the task dirs on disk.
     */
    private static StartedProcessor startStandaloneProcessor(Path processorPath) {
        final Globals globals = new Globals();
        globals.processor.enabled = true;
        globals.standalone.active = true;
        globals.processorPath = processorPath;

        final AdditionalCustomUserDetails userDetails = new AdditionalCustomUserDetails(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        userDetails.init();
        final ProcessorEnvironment processorEnvironment = new ProcessorEnvironment(globals, userDetails, null);

        final ProcessorTaskService service = new ProcessorTaskService(globals, null, processorEnvironment);
        service.postConstruct();

        final Set<ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef> cores =
            processorEnvironment.getProcessorEnv().metadataParams().getAllEnabledRefsForCores();
        assertEquals(1, cores.size(), "standalone processor must have exactly one enabled core");
        return new StartedProcessor(service, cores.iterator().next());
    }

    private static Restart restartCapturingLog(Path processorPath) {
        final Logger logger = (Logger) LoggerFactory.getLogger(ProcessorTaskService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final StartedProcessor processor;
        try {
            processor = startStandaloneProcessor(processorPath);
        } finally {
            logger.detachAppender(appender);
        }
        final List<String> messages;
        // AppenderBase.doAppend() is synchronized on the appender; other @Test methods log concurrently
        synchronized (appender) {
            messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }
        return new Restart(processor, messages);
    }

    private static ProcessorCoreTask newTask(StartedProcessor processor, long taskId) {
        final ProcessorCoreTask task = new ProcessorCoreTask();
        task.taskId = taskId;
        task.execContextId = 42L;
        task.dispatcherUrl = processor.core().dispatcherUrl.url;
        task.createdOn = System.currentTimeMillis();
        return task;
    }

    private static void seedTaskOnDisk(StartedProcessor processor, ProcessorCoreTask task) throws IOException {
        final Path taskDir = processor.service().prepareTaskDir(processor.core(), task.taskId);
        ProcessorTaskService.actualSave(task, taskDir, taskDir.resolve(Consts.TASK_YAML));
    }

    private static boolean isFoundDirLogged(List<String> messages, long taskId) {
        final String marker = "Found dir of task with id: " + taskId + ", ";
        return messages.stream().anyMatch(m -> m.contains(marker));
    }

    private static List<Long> tasksSelectedForExecution(StartedProcessor processor) {
        return processor.service().findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(processor.core(), false)
            .stream().map(t -> t.taskId).sorted().toList();
    }

    private static String okFunctionExec() {
        return FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            new FunctionApiData.SystemExecResult("mh.test-function", true, 0, ""), null, null, null));
    }

    @Test
    public void test_postConstruct_completedTask_isLoadedButNotLogged(@TempDir Path processorPath) throws IOException {
        final StartedProcessor first = startStandaloneProcessor(processorPath);

        final ProcessorCoreTask completed = newTask(first, 9101L);
        completed.launchedOn = System.currentTimeMillis();
        completed.finishedOn = System.currentTimeMillis();
        completed.functionExecResult = okFunctionExec();
        completed.reported = true;
        completed.reportedOn = System.currentTimeMillis();
        completed.delivered = true;
        completed.completed = true;
        seedTaskOnDisk(first, completed);
        seedTaskOnDisk(first, newTask(first, 9102L));

        final Restart restart = restartCapturingLog(processorPath);

        assertTrue(isFoundDirLogged(restart.messages(), 9102L), "a not finished task will be re-run and must be logged");
        assertFalse(isFoundDirLogged(restart.messages(), 9101L), "a task which won't be re-run must not be logged");

        assertNotNull(restart.processor().service().findByIdForCore(restart.processor().core(), 9101L), "a completed task must still be loaded");
        assertEquals(List.of(9102L), tasksSelectedForExecution(restart.processor()));
    }

    @Test
    public void test_postConstruct_finishedNotReportedTask_isLoadedButNotLogged(@TempDir Path processorPath) throws IOException {
        final StartedProcessor first = startStandaloneProcessor(processorPath);

        final ProcessorCoreTask finished = newTask(first, 9201L);
        finished.launchedOn = System.currentTimeMillis();
        finished.finishedOn = System.currentTimeMillis();
        finished.functionExecResult = okFunctionExec();
        seedTaskOnDisk(first, finished);
        seedTaskOnDisk(first, newTask(first, 9202L));

        final Restart restart = restartCapturingLog(processorPath);

        assertTrue(isFoundDirLogged(restart.messages(), 9202L), "a not finished task will be re-run and must be logged");
        assertFalse(isFoundDirLogged(restart.messages(), 9201L), "a task which won't be re-run must not be logged");

        final List<Long> forReporting = restart.processor().service().getForReporting(restart.processor().core())
            .stream().map(t -> t.taskId).toList();
        assertEquals(List.of(9201L), forReporting, "a finished task goes to reporting, not to execution");
        assertEquals(List.of(9202L), tasksSelectedForExecution(restart.processor()));
    }

    @Test
    public void test_postConstruct_reportedNotFinishedTask_isLoadedButNotLogged(@TempDir Path processorPath) throws IOException {
        final StartedProcessor first = startStandaloneProcessor(processorPath);

        // the state TaskAssetPreparer.fixedDelay() leaves for a task of a finished ExecContext:
        // reported and delivered without being finished. TaskProcessor deletes such a task instead of running it.
        final ProcessorCoreTask reported = newTask(first, 9301L);
        reported.reported = true;
        reported.reportedOn = System.currentTimeMillis();
        reported.delivered = true;
        reported.output.outputStatuses.add(new ProcessorCoreTask.OutputStatus(77L, false));
        seedTaskOnDisk(first, reported);
        seedTaskOnDisk(first, newTask(first, 9302L));

        final Restart restart = restartCapturingLog(processorPath);

        assertTrue(isFoundDirLogged(restart.messages(), 9302L), "a not finished task will be re-run and must be logged");
        assertFalse(isFoundDirLogged(restart.messages(), 9301L), "a task which won't be re-run must not be logged");

        final ProcessorCoreTask loaded = restart.processor().service().findByIdForCore(restart.processor().core(), 9301L);
        assertNotNull(loaded, "a reported task must still be loaded");
        assertTrue(loaded.reported);
        assertNull(loaded.finishedOn);
    }

    @Test
    public void test_postConstruct_taskMarkedAsFinishedWhileLoading_isNotLogged(@TempDir Path processorPath) throws IOException {
        final StartedProcessor first = startStandaloneProcessor(processorPath);

        // functionExecResult with a failed generalExec but finishedOn==null - postConstruct() fixes the state
        // via markAsFinished() while loading, so the task won't be re-run
        final ProcessorCoreTask failed = newTask(first, 9401L);
        failed.launchedOn = System.currentTimeMillis();
        failed.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            new FunctionApiData.SystemExecResult("mh.test-function", true, 0, ""), null, null,
            new FunctionApiData.SystemExecResult("mh.test-general", false, -1, "general exec failed")));
        seedTaskOnDisk(first, failed);
        seedTaskOnDisk(first, newTask(first, 9402L));

        final Restart restart = restartCapturingLog(processorPath);

        assertTrue(isFoundDirLogged(restart.messages(), 9402L), "a not finished task will be re-run and must be logged");
        assertFalse(isFoundDirLogged(restart.messages(), 9401L), "a task which won't be re-run must not be logged");

        final ProcessorCoreTask loaded = restart.processor().service().findByIdForCore(restart.processor().core(), 9401L);
        assertNotNull(loaded);
        assertNotNull(loaded.finishedOn, "the state of task must be fixed while loading");
        assertTrue(loaded.completed);
        assertEquals(List.of(9402L), tasksSelectedForExecution(restart.processor()));
    }
}
