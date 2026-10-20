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
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTaskUtils;
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

    // ---- the hidden contract of 'console': only the text of an error travels to the Dispatcher ----
    // Every @Test uses its own taskIds, as above.

    private static final String CHATTY = "a Function printed its progress to stdout";

    private static FunctionApiData.SystemExecResult ok(String functionCode, String console) {
        return new FunctionApiData.SystemExecResult(functionCode, true, 0, console);
    }

    /** what reportTaskProcessingResult() sends to the Dispatcher for the task */
    private static FunctionApiData.FunctionExec reportedFunctionExec(StartedProcessor processor, long taskId) {
        final ProcessorCommParamsYaml.ReportTaskProcessingResult report = processor.service().reportTaskProcessingResult(processor.core());
        assertNotNull(report, "a finished task must be reported, task #" + taskId);
        assertNotNull(report.results);
        final String result = report.results.stream().filter(r -> r.taskId == taskId).findFirst().orElseThrow().result;
        final FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(result);
        assertNotNull(functionExec, "reported functionExecResult, task #" + taskId);
        return functionExec;
    }

    /** the functionExecResult stored in task.yaml on disk */
    private static FunctionApiData.FunctionExec storedFunctionExec(StartedProcessor processor, long taskId) {
        final Path taskYaml = processor.service().prepareTaskDir(processor.core(), taskId).resolve(Consts.TASK_YAML);
        final FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(ProcessorTaskUtils.to(taskYaml).functionExecResult);
        assertNotNull(functionExec, "stored functionExecResult, task #" + taskId);
        return functionExec;
    }

    private static void assertConsoles(FunctionApiData.FunctionExec fe, String where,
                                       String exec, String pre, String post, String general) {
        assertNotNull(fe.exec, where + ": exec");
        assertNotNull(fe.preExecs, where + ": preExecs");
        assertNotNull(fe.postExecs, where + ": postExecs");
        assertNotNull(fe.generalExec, where + ": generalExec");
        assertAll(where,
            () -> assertEquals(exec, fe.exec.console, where + ": exec.console"),
            () -> assertEquals(pre, fe.preExecs.getFirst().console, where + ": preExecs[0].console"),
            () -> assertEquals(post, fe.postExecs.getFirst().console, where + ": postExecs[0].console"),
            () -> assertEquals(general, fe.generalExec.console, where + ": generalExec.console"));
    }

    @Test
    public void test_postConstruct_consoleOfOkExecs(@TempDir Path processorPath) throws IOException {
        final StartedProcessor first = startStandaloneProcessor(processorPath);

        // finished, not reported yet - every exec is ok and every one of them printed to stdout
        final ProcessorCoreTask allOk = newTask(first, 9501L);
        allOk.launchedOn = System.currentTimeMillis();
        allOk.finishedOn = System.currentTimeMillis();
        allOk.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            ok("mh.test-function", CHATTY + " (exec)"),
            List.of(ok("mh.test-pre", CHATTY + " (pre)")),
            List.of(ok("mh.test-post", CHATTY + " (post)")),
            ok("mh.test-general", CHATTY + " (general)")));
        seedTaskOnDisk(first, allOk);

        // an ok exec which printed to stdout, and a failed generalExec - postConstruct() fixes the state via markAsFinished()
        final ProcessorCoreTask mixed = newTask(first, 9502L);
        mixed.launchedOn = System.currentTimeMillis();
        mixed.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            ok("mh.test-function", CHATTY + " (exec)"), null, null,
            new FunctionApiData.SystemExecResult("mh.test-general", false, -1, "01.330.010 output Variable #77 violates contract")));
        seedTaskOnDisk(first, mixed);

        final StartedProcessor restarted = startStandaloneProcessor(processorPath);

        // PHASE #1: init() enforces the contract - the console of an ok exec loaded from disk is cleaned, on disk and in the report
        assertConsoles(storedFunctionExec(restarted, 9501L), "task #9501 on disk", "", "", "", "");
        assertConsoles(reportedFunctionExec(restarted, 9501L), "task #9501 reported", "", "", "", "");

        final FunctionApiData.FunctionExec mixedOnDisk = storedFunctionExec(restarted, 9502L);
        assertNotNull(mixedOnDisk.exec);
        assertNotNull(mixedOnDisk.generalExec);
        assertEquals("", mixedOnDisk.exec.console, "task #9502 on disk: exec.console");
        assertEquals("01.330.010 output Variable #77 violates contract", mixedOnDisk.generalExec.console,
            "task #9502 on disk: the console of a failed generalExec must be kept");
    }

    @Test
    public void test_postConstruct_consoleOfFailedExecs_isKept(@TempDir Path processorPath) throws IOException {
        final StartedProcessor first = startStandaloneProcessor(processorPath);

        final ProcessorCoreTask failed = newTask(first, 9601L);
        failed.launchedOn = System.currentTimeMillis();
        failed.finishedOn = System.currentTimeMillis();
        failed.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            new FunctionApiData.SystemExecResult("mh.test-function", false, 1, "Traceback (most recent call last)"), null, null, null));
        seedTaskOnDisk(first, failed);

        // exitCode==0 without isOk==true is an inconsistent state, i.e. not a successful exec - its console is kept
        final ProcessorCoreTask inconsistent = newTask(first, 9602L);
        inconsistent.launchedOn = System.currentTimeMillis();
        inconsistent.finishedOn = System.currentTimeMillis();
        inconsistent.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            new FunctionApiData.SystemExecResult("mh.test-function", false, 0, "exitCode 0, but not ok"), null, null, null));
        seedTaskOnDisk(first, inconsistent);

        final StartedProcessor restarted = startStandaloneProcessor(processorPath);

        final FunctionApiData.FunctionExec failedReported = reportedFunctionExec(restarted, 9601L);
        assertNotNull(failedReported.exec);
        assertEquals("Traceback (most recent call last)", failedReported.exec.console, "task #9601 reported: exec.console");

        final FunctionApiData.FunctionExec inconsistentOnDisk = storedFunctionExec(restarted, 9602L);
        assertNotNull(inconsistentOnDisk.exec);
        assertEquals("exitCode 0, but not ok", inconsistentOnDisk.exec.console, "task #9602 on disk: exec.console");
    }

    @Test
    public void test_markAsFinished_consoleOfOkExec(@TempDir Path processorPath) {
        final StartedProcessor processor = startStandaloneProcessor(processorPath);

        final ProcessorCoreTask launched = newTask(processor, 9701L);
        launched.launchedOn = System.currentTimeMillis();
        processor.service().addTakToCore(processor.core(), launched);

        processor.service().markAsFinished(processor.core(), 9701L, new FunctionApiData.FunctionExec(
            ok("mh.test-function", CHATTY + " (exec)"),
            List.of(ok("mh.test-pre", CHATTY + " (pre)")),
            List.of(ok("mh.test-post", CHATTY + " (post)")),
            ok("mh.test-general", CHATTY + " (general)")));

        // PHASE #2: the Processor doesn't store the console of an ok exec - neither on disk nor in the report
        assertConsoles(storedFunctionExec(processor, 9701L), "task #9701 on disk", "", "", "", "");
        assertConsoles(reportedFunctionExec(processor, 9701L), "task #9701 reported", "", "", "", "");
    }

    @Test
    public void test_markAsFinished_consoleOfFailedExec_isKept(@TempDir Path processorPath) {
        final StartedProcessor processor = startStandaloneProcessor(processorPath);

        final ProcessorCoreTask launched = newTask(processor, 9801L);
        launched.launchedOn = System.currentTimeMillis();
        processor.service().addTakToCore(processor.core(), launched);

        processor.service().markAsFinished(processor.core(), 9801L, new FunctionApiData.FunctionExec(
            new FunctionApiData.SystemExecResult("mh.test-function", false, 2, "Traceback (most recent call last)"), null, null, null));

        final FunctionApiData.FunctionExec reported = reportedFunctionExec(processor, 9801L);
        assertNotNull(reported.exec);
        assertEquals("Traceback (most recent call last)", reported.exec.console, "task #9801 reported: exec.console");
        assertFalse(reported.exec.isOk);
        assertEquals(2, reported.exec.exitCode);
    }

    private static FunctionApiData.FunctionExec functionExecIn(Path taskYaml) {
        final FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(ProcessorTaskUtils.to(taskYaml).functionExecResult);
        assertNotNull(functionExec, "functionExecResult in " + taskYaml);
        assertNotNull(functionExec.exec, "exec in " + taskYaml);
        return functionExec;
    }

    @Test
    public void test_enforceConsoleContract_cleanedConsole_isWrittenImmediately(@TempDir Path taskDir) throws IOException {
        final Path taskYaml = taskDir.resolve(Consts.TASK_YAML);
        final ProcessorCoreTask task = new ProcessorCoreTask();
        task.taskId = 9901L;
        task.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(ok("mh.test-function", CHATTY), null, null, null));
        ProcessorTaskService.actualSave(task, taskDir, taskYaml);

        final FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.functionExecResult);
        assertNotNull(functionExec);
        assertTrue(ProcessorTaskService.enforceConsoleContract(task, functionExec, taskDir, taskYaml));

        assertEquals("", functionExecIn(taskYaml).exec.console, "task.yaml must be re-written with the cleaned console");
        // actualSave() keeps the previous task.yaml as task.yaml.bak - its presence proves the write went through actualSave()
        assertEquals(CHATTY, functionExecIn(taskDir.resolve(Consts.TASK_YAML + ".bak")).exec.console);
    }

    @Test
    public void test_enforceConsoleContract_nothingToClean_isNotWritten(@TempDir Path taskDir) throws IOException {
        final Path taskYaml = taskDir.resolve(Consts.TASK_YAML);
        final ProcessorCoreTask task = new ProcessorCoreTask();
        task.taskId = 9902L;
        task.functionExecResult = FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
            new FunctionApiData.SystemExecResult("mh.test-function", false, 1, "Traceback (most recent call last)"), null, null, null));
        ProcessorTaskService.actualSave(task, taskDir, taskYaml);

        final FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.functionExecResult);
        assertNotNull(functionExec);
        assertFalse(ProcessorTaskService.enforceConsoleContract(task, functionExec, taskDir, taskYaml));

        assertTrue(Files.notExists(taskDir.resolve(Consts.TASK_YAML + ".bak")), "nothing was cleaned, task.yaml must not be re-written");
        assertEquals("Traceback (most recent call last)", functionExecIn(taskYaml).exec.console);
    }
}
