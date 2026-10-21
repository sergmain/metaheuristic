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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTaskUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DigitUtils;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;
import static java.nio.file.StandardOpenOption.*;

/**
 * <p>Error code prefix: {@code 01.713.} (unique to this class).
 */
@SuppressWarnings({"WeakerAccess", "DuplicatedCode"})
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final ProcessorEnvironment processorEnvironment;
    // read the files of FINISHED ExecContexts in its postConstruct(), i.e. before init() of this service
    private final FinishedExecContextsService finishedExecContextsService;

    /**
     * key - code of core
     * value:
     * Map of:
     * key - DispatcherUrl
     * value:
     * Map.of:
     * key - ProcessorCoreTask.taskId,
     * Value - ProcessorCoreTask
     */
    private final ConcurrentHashMap<String, Map<DispatcherUrl, Map<Long, ProcessorCoreTask>>> map = new ConcurrentHashMap<>();

    /**
     * A Task with a dir on disk - what ArtifactCleanerAtProcessor needs to clean the dir of a Task which isn't loaded.
     */
    public record TaskShortInfo(long id, @Nullable Long execContextId) {}

    /**
     * Every Task with a dir on disk, including the Tasks of FINISHED ExecContexts which weren't loaded into {@link #map}
     * at startup. ArtifactCleanerAtProcessor cleans by it, so the dir of a not loaded Task is deleted as well once
     * its ExecContext doesn't exist any more.
     * key - DispatcherUrl
     * value:
     * Map of:
     * key - code of core
     * value:
     * Map of:
     * key - taskId,
     * value - TaskShortInfo
     */
    private final ConcurrentHashMap<DispatcherUrl, ConcurrentHashMap<String, ConcurrentHashMap<Long, TaskShortInfo>>> allTasks = new ConcurrentHashMap<>();

    public Path processorPath;

    /**
     * @return the key of the lock of a Task, see {@link ProcessorTaskSyncService} - the absolute path to its task.yaml,
     * resolved as {@link #save} resolves it. Static and Path-based on purpose - it's testable without a Spring context.
     */
    public static String taskYamlKey(Path processorPath, ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        final Path dispatcherDir = processorPath.resolve(core.coreCode).resolve(Consts.TASK_DIR).resolve(MetadataParams.asCode(core.dispatcherUrl));
        return ProcessorTaskSyncService.key(DirUtils.getPoweredPath(dispatcherDir, taskId).resolve(Consts.TASK_YAML));
    }

    private String taskYamlKey(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        return taskYamlKey(processorPath, core, taskId);
    }

   @PostConstruct
    public void postConstruct() {
       if (globals.testing || !globals.processor.enabled) {
           return;
       }
       this.processorPath = globals.processorPath;
       long mills = System.currentTimeMillis();
       init(processorPath);
       long endMills = System.currentTimeMillis();
       log.info("713.020 ProcessorTaskService.postConstruct() was finished for {} milliseconds", endMills - mills);
       //noinspection unused
       int i = 0;
    }

    @SneakyThrows
    private void init(Path processorPath) {
        // stage 1 - the dirs of Tasks which don't belong to a FINISHED ExecContext, see collectTaskDir()
        final LinkedList<CompletableFuture<@Nullable TaskDir>> collected = new LinkedList<>();
        // stage 2 - what's done with the dir of each collected Task, see processTaskDir()
        final LinkedList<CompletableFuture<Void>> list = new LinkedList<>();
        // Tasks of FINISHED ExecContexts which weren't loaded
        final LongAdder ofFinished = new LongAdder();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.getProcessorEnv().metadataParams().getAllEnabledRefsForCores()) {

                Path processorDir = processorPath.resolve(core.coreCode);
                Path processorTaskDir = processorDir.resolve(Consts.TASK_DIR);
                String dispatcherCode = MetadataParams.asCode(core.dispatcherUrl);
                Path dispatcherDir = processorTaskDir.resolve(dispatcherCode);
                if (Files.notExists(dispatcherDir)) {
                    Files.createDirectories(dispatcherDir);
                }

                try {
                    DispatcherUrl dispatcherUrl = core.dispatcherUrl;

                    // !!! do not remove try(Stream<Path>){}
                    try (final Stream<Path> pathStream = Files.list(dispatcherDir)) {
                        pathStream.forEach(p -> {
                            final Path taskGroupDir = p;
                            if (!Files.isDirectory(taskGroupDir)) {
                                return;
                            }
                            try {
                                try (final Stream<Path> stream = Files.list(p)) {
                                    stream.map(taskDir -> CompletableFuture.supplyAsync(() -> collectTaskDir(core, taskDir, taskGroupDir, dispatcherUrl, ofFinished), executor))
                                        .collect(Collectors.toCollection(() -> collected));
                                }
                            } catch (IOException e) {
                                String es = "713.040 Error";
                                log.error(es, e);
                                throw new RuntimeException(es, e);
                            }
                        });
                    }
                } catch (IOException e) {
                    String es = "713.060 Error";
                    log.error(es, e);
                    throw new RuntimeException(es, e);
                }
            }
            CompletableFuture.allOf(collected.toArray(CompletableFuture[]::new)).join();
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            collected.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .map(d -> CompletableFuture.supplyAsync(() -> processTaskDir(d.core(), d.taskDir(), d.taskGroupDir(), d.dispatcherUrl(), ofFinished), executor))
                    .collect(Collectors.toCollection(() -> list));
            CompletableFuture.allOf(list.toArray(CompletableFuture[]::new)).join();
        }
        if (ofFinished.sum() > 0) {
            log.info("01.713.700 {} Task(s) of FINISHED ExecContexts weren't loaded, their dirs are kept as is", ofFinished.sum());
        }
    }

    /** a dir of a Task which goes to stage 2 of the startup scan, processTaskDir() */
    private record TaskDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, Path taskGroupDir, DispatcherUrl dispatcherUrl) {}

    private static final String EXEC_CONTEXT_ID_KEY = "execContextId:";

    /**
     * Stage 1 of the startup scan. A Task of a FINISHED ExecContext is registered as a Task with a dir and skipped - its
     * task.yaml isn't parsed and its dir is kept as is. Every other dir goes to stage 2, processTaskDir(), unchanged -
     * including a dir whose ExecContext can't be read here: processTaskDir() parses task.yaml as a whole and checks again.
     *
     * @return the dir for stage 2, or null for a skipped Task
     */
    private @Nullable TaskDir collectTaskDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, Path taskGroupDir,
                                             DispatcherUrl dispatcherUrl, LongAdder ofFinished) {
        final Long execContextId = readExecContextId(taskDir.resolve(Consts.TASK_YAML));
        if (execContextId != null && finishedExecContextsService.isFinished(dispatcherUrl, execContextId)) {
            final Long taskId = taskIdOf(taskGroupDir, taskDir);
            if (taskId != null) {
                getAllTasksForProcessorCore(core).put(taskId, new TaskShortInfo(taskId, execContextId));
                ofFinished.increment();
                return null;
            }
        }
        return new TaskDir(core, taskDir, taskGroupDir, dispatcherUrl);
    }

    /**
     * The id of the ExecContext of a Task, read from its task.yaml without parsing the whole file: the value of the
     * top-level key 'execContextId', i.e. at the very start of a line. A nested key of the same name - TaskParamsYaml
     * in 'params' has one - is indented, so it's never taken for it. Lines are read only up to the key.
     * Path-based and static on purpose - it's testable without a Spring context.
     *
     * @return the id, or null when there isn't a readable one
     */
    public static @Nullable Long readExecContextId(Path taskYaml) {
        if (Files.notExists(taskYaml)) {
            return null;
        }
        try (Stream<String> lines = Files.lines(taskYaml, StandardCharsets.UTF_8)) {
            return lines.filter(l -> l.startsWith(EXEC_CONTEXT_ID_KEY))
                    .findFirst()
                    .map(l -> toLongOrNull(l.substring(EXEC_CONTEXT_ID_KEY.length()).strip()))
                    .orElse(null);
        } catch (Throwable th) {
            return null;
        }
    }

    private static @Nullable Long toLongOrNull(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return the id of a Task from the names of its dirs, as processTaskDir() computes it, or null when they aren't numbers */
    private static @Nullable Long taskIdOf(Path taskGroupDir, Path taskDir) {
        try {
            return Long.parseLong(taskGroupDir.getFileName().toString()) * DigitUtils.DIV + Long.parseLong(taskDir.getFileName().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Void processTaskDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, Path taskGroupDir, DispatcherUrl dispatcherUrl,
                                LongAdder ofFinished) {
        try {
            if (!Thread.currentThread().isVirtual()) {
                throw new IllegalStateException("(!Thread.currentThread().isVirtual())");
            }
            String groupDirName = taskGroupDir.getFileName().toString();
            String name = taskDir.getFileName().toString();
            long taskId = Long.parseLong(groupDirName) * DigitUtils.DIV + Long.parseLong(name);
            Path taskYamlFile = taskDir.resolve(Consts.TASK_YAML);
            boolean exists = Files.exists(taskYamlFile);
            if (!exists || Files.size(taskYamlFile) == 0L) {
                deleteDir(taskDir, "Delete not valid dir of task " + taskDir + ", exist: " + exists + ", length: " + (exists ? Files.size(taskYamlFile) : "<none>"));
                return null;
            }

            try (InputStream fis = Files.newInputStream(taskYamlFile)) {
                ProcessorCoreTask task = ProcessorTaskUtils.to(fis);
                if (S.b(task.dispatcherUrl)) {
                    deleteDir(taskDir, "713.080 Delete not valid dir of task " + taskDir);
                    log.warn("713.100 task #{} from dispatcher {} was deleted from disk because dispatcherUrl field was empty", taskId, dispatcherUrl);
                    return null;
                }
                if (task.taskId==null || taskId!=task.taskId) {
                    deleteDir(taskDir, "713.110 Delete not valid dir of task " + taskDir);
                    log.warn("713.115 task #{} from dispatcher {} is broken - (task.taskId==null || taskId!=task.taskId). Will be deleted", taskId, dispatcherUrl);
                    return null;
                }
                // the dir is known in any case, a Task of a FINISHED ExecContext included
                getAllTasksForProcessorCore(core).put(taskId, new TaskShortInfo(taskId, task.execContextId));
                // usually already skipped by stage 1, collectTaskDir() - here for a Task whose execContextId it couldn't read
                if (finishedExecContextsService.isFinished(dispatcherUrl, task.execContextId)) {
                    // there is nothing to do with a Task of a FINISHED ExecContext - it isn't loaded, its dir is kept as is
                    ofFinished.increment();
                    return null;
                }
                addTakToCore(core, task);

                // fix state of task
                FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResult());
                // enforce the hidden contract of 'console' for a task which was stored on disk before it was enforced:
                // only the text related to an error is allowed there, a cleaned console is written to task.yaml immediately
                if (functionExec != null) {
                    enforceConsoleContract(task, functionExec, taskDir, taskYamlFile);
                }
                if (functionExec != null &&
                        ((functionExec.generalExec != null && !functionExec.exec.isOk) ||
                                (functionExec.generalExec != null && !functionExec.generalExec.isOk))) {
                    markAsFinished(core, taskId, functionExec);
                }

                // must be checked after fixing the state - markAsFinished() changes this instance of task
                if (isTaskForRerun(task)) {
                    log.info("01.713.680 Found dir of task with id: {}, {}, {}, {}", taskId, groupDirName, name, dispatcherUrl.url);
                }
            }
        } catch (IOException e) {
            String es = "713.140 Error";
            log.error(es, e);
            throw new RuntimeException(es, e);
        } catch (YAMLException e) {
            String es = "713.160 yaml Error: " + e.getMessage();
            log.warn(es, e);
            deleteDir(taskDir, "Delete not valid dir of task " + taskDir);
        }
        return null;
    }

    /**
     * A task loaded from disk will be re-run only if it wasn't completed, finished or reported.
     * A reported but not finished task belongs to a finished ExecContext (see TaskAssetPreparer.fixedDelay()),
     * TaskProcessor deletes such a task instead of running it.
     */
    public static boolean isTaskForRerun(ProcessorCoreTask task) {
        return !task.completed && task.finishedOn == null && !task.reported;
    }

    /**
     * The hidden contract of {@link FunctionApiData.SystemExecResult#console}: only the text related to an error of
     * a Function is allowed there. Everything else the Dispatcher needs must be returned via output Variables.
     * The console of a successful exec - exitCode==0 and isOk==true - is cleaned. The console of any other exec is kept
     * as is, including an inconsistent one with exitCode==0 and isOk==false, which isn't a successful exec.
     * Static on purpose - it's testable without a Spring context.
     *
     * @return true when at least one console was cleaned, i.e. functionExec was changed
     */
    public static boolean cleanConsoleOfOkExecs(FunctionApiData.FunctionExec functionExec) {
        final List<FunctionApiData.SystemExecResult> okExecsWithConsole = Stream.of(
                        Stream.ofNullable(functionExec.exec),
                        Stream.ofNullable(functionExec.preExecs).flatMap(List::stream),
                        Stream.ofNullable(functionExec.postExecs).flatMap(List::stream),
                        Stream.ofNullable(functionExec.generalExec))
                .flatMap(s -> s)
                .filter(o -> o.isOk && o.exitCode == 0 && !"".equals(o.console))
                .toList();
        okExecsWithConsole.forEach(o -> o.console = "");
        return !okExecsWithConsole.isEmpty();
    }

    /**
     * Enforces the contract of 'console' (see {@link #cleanConsoleOfOkExecs}) on a stored task: when a console was
     * cleaned, the task with the cleaned functionExec is written to taskYaml immediately via {@link #actualSave}.
     * Nothing is written when there was nothing to clean.
     * Under the lock of this Task only - see {@link ProcessorTaskSyncService}, the key is taskYaml.
     * Path-based and static on purpose - it's testable without a Spring context.
     *
     * @return true when a console was cleaned and the task was written
     */
    public static boolean enforceConsoleContract(ProcessorCoreTask task, FunctionApiData.FunctionExec functionExec, Path taskDir, Path taskYaml) throws IOException {
        if (!cleanConsoleOfOkExecs(functionExec)) {
            return false;
        }
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(ProcessorTaskSyncService.key(taskYaml));
        try {
            lock.lock();
            task.setFunctionExecResult(FunctionExecUtils.toString(functionExec));
            actualSave(task, taskDir, taskYaml);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public static void deleteDir(Path f, String info) {
        log.warn(info + ", file: " + f.toAbsolutePath());
        try {
            PathUtils.deleteDirectory(f);
        } catch (IOException e) {
            log.warn("713.180 Error while deleting dir {}, error: {}", f, e.toString());
        }
    }

    public void setReportedOn(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            log.info("713.200 setReportedOn({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.220 ProcessorCoreTask wasn't found for Id " + taskId);
                return;
            }
            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(core, task);
        } finally {
            lock.unlock();
        }
    }

    public void setInputAsEmpty(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId, String variableId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            log.info("713.240 setInputAsEmpty({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.260 ProcessorCoreTask wasn't found for Id " + taskId);
                return;
            }
            final ProcessorCoreTask.EmptyStateOfInput input = task.empty.empties.stream().filter(o -> o.variableId.equals(variableId)).findFirst().orElse(null);
            if (input == null) {
                task.empty.empties.add(new ProcessorCoreTask.EmptyStateOfInput(variableId, true));
            } else {
                input.empty = true;
            }

            // TODO P0 2022-04-27 is that bug or what's meaning of this?
//            task.setReported(true);
//            task.setReportedOn(System.currentTimeMillis());

            save(core, task);
        } finally {
            lock.unlock();
        }
    }

    public void setDelivered(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();

            log.info("713.280 setDelivered({}, {})", core.dispatcherUrl.url, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.300 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            if (!task.isReported()) {
                log.warn("713.320 This state need to be investigated, task wasn't reported to dispatcher");
            }
            if (task.delivered) {
                return;
            }

            task.setDelivered(true);
            // if function has finished with an error,
            // then we don't have to set isCompleted any more
            // because we've already marked this task as completed
            if (!task.isCompleted()) {
                FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResult());
                if (functionExec != null && !functionExec.allFunctionsAreOk()) {
                    task.setCompleted(true);
                } else {
                    task.setCompleted(task.output.allUploaded());
                }
            }
            save(core, task);
        } finally {
            lock.unlock();
        }
    }

    public void setVariableUploadedAndCompleted(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, Long outputVariableId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            log.info("setResourceUploadedAndCompleted({}, {}, {})", core.dispatcherUrl, taskId, outputVariableId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.340 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            task.output.outputStatuses.stream().filter(o -> o.variableId.equals(outputVariableId)).findFirst().ifPresent(status -> status.uploaded = true);
            task.setCompleted(task.isDelivered());
            save(core, task);
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unused")
    public void setCompleted(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            log.info("setCompleted({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.360 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            task.setCompleted(true);
            save(core, task);
        } finally {
            lock.unlock();
        }
    }

    public List<ProcessorCoreTask> getForReporting(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        // the state of each task - several fields - is read under the lock of that task
        List<ProcessorCoreTask> result = getTasksForProcessorCore(core).values().stream()
            .filter(o -> ProcessorTaskSyncService.getWithReadSync(taskYamlKey(core, o.taskId), () -> isForReporting(o)))
            .collect(Collectors.toList());
        return result;
    }

    private static boolean isForReporting(ProcessorCoreTask processorTask) {
        return processorTask.finishedOn != null &&
            (!processorTask.isReported() ||
                (!processorTask.isDelivered() &&
                    (processorTask.getReportedOn() == null || (System.currentTimeMillis() - processorTask.getReportedOn()) > 60_000)));
    }

    /** the state of a task which is reported - read in one go, under the lock of the task */
    private record ReportState(boolean delivered, boolean reported, @Nullable String functionExecResult) {}

    public ProcessorCommParamsYaml.@Nullable ReportTaskProcessingResult reportTaskProcessingResult(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        final List<ProcessorCoreTask> list = getForReporting(core);
        if (list.isEmpty()) {
            return null;
        }
        log.info("Number of tasks for reporting: " + list.size());
        final ProcessorCommParamsYaml.ReportTaskProcessingResult processingResult = new ProcessorCommParamsYaml.ReportTaskProcessingResult();
        for (ProcessorCoreTask task : list) {
            // read under the lock of the task, so a task which is being finished right now isn't reported half-done;
            // setReportedOn() below locks it for writing
            final ReportState state = ProcessorTaskSyncService.getWithReadSync(taskYamlKey(core, task.taskId),
                    () -> new ReportState(task.isDelivered(), task.isReported(), task.getFunctionExecResult()));
            if (state.delivered() && !state.reported()) {
                log.warn("01.713.380 This state need to be investigated: (task.isDelivered() && !task.isReported())==true");
            }
            // TODO 2019-07-12 do we need to check against task.isReported()? isn't task.isDelivered() just enough?
            //  2020-09-26 until 713.105 (task.isDelivered() && !task.isReported() ) will be fixed, this check is correct
            //  2020-11-23 looks like 713.105 can occur in case when a task was finished with status ERROR
            if (state.delivered() && state.reported()) {
                continue;
            }
            final ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result =
                    new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getTaskId(), state.functionExecResult());
            processingResult.results.add(result);
            setReportedOn(core, task.taskId);
        }
        return processingResult;
    }

    public void markAsFinishedWithError(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId, String es) {
        // markAsFinished() locks the task itself - a nested lock of the same task isn't allowed
        markAsFinished(core, taskId,
                new FunctionApiData.FunctionExec(
                        null, null, null,
                        new FunctionApiData.SystemExecResult("system-error", false, -992, es)));
    }

    void markAsFinished(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, FunctionApiData.FunctionExec functionExec) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            log.info("markAsFinished({}, #{}, {})", core.dispatcherUrl.url, taskId, functionExec);

            processorEnvironment.getProcessorEnv().metadataParams().removeQuota(core.dispatcherUrl.url, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.400 ProcessorCoreTask wasn't found for Id #" + taskId);
            } else {
                if (task.getLaunchedOn() == null) {
                    final TaskParamsYaml tpy = TaskParamsYamlUtils.UTILS.to(task.getParams());
                    log.info("713.420 task #{}, function '{}', doesn't have the launchedOn as inited", taskId, tpy.task.function.code);
                    final String es = "713.440 stacktrace";
                    try {
                        throw new RuntimeException(es);
                    } catch (RuntimeException e) {
                        log.info(es, e);
                    }
                    task.setLaunchedOn(System.currentTimeMillis());
                }
                if (!functionExec.allFunctionsAreOk()) {
                    log.info("713.460 task #{} was finished with an error, set completed to true", taskId);
                    task.setCompleted(true);
                } else {
                    task.setCompleted(false);
                }
                task.setFinishedOn(System.currentTimeMillis());
                task.setReported(false);
                // only the text related to an error is allowed in 'console', see cleanConsoleOfOkExecs()
                cleanConsoleOfOkExecs(functionExec);
                task.setFunctionExecResult(FunctionExecUtils.toString(functionExec));

                save(core, task);
            }
        } finally {
            lock.unlock();
        }
    }

    void markAsAssetPrepared(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, boolean status) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            log.info("markAsAssetPrepared(dispatcherUrl: {}, taskId: {}, status: {})", core.dispatcherUrl, taskId, status);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.460 ProcessorCoreTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(status);
                save(core, task);
            }
        } finally {
            lock.unlock();
        }
    }

    boolean isNeedNewTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        // TODO 2019-10-24 need to optimize
        List<ProcessorCoreTask> tasks = findAllByCompletedIsFalse(core);
        for (ProcessorCoreTask task : tasks) {
            // we don't need new task because execContext for this task is active
            // i.e. there is a non-completed task with active execContext
            // if execContext wasn't active, we would need a new task
            if (currentExecState.isStarted(new DispatcherUrl(task.dispatcherUrl), task.execContextId)) {
                return false;
            }
        }
        return true;
    }

    public List<ProcessorCoreTask> findAllByCompletedIsFalse(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        List<ProcessorCoreTask> list = new ArrayList<>();
        for (ProcessorCoreTask task : getTasksForProcessorCore(core).values()) {
            // decided - and the task completed when everything is done - under the lock of the task
            if (!ProcessorTaskSyncService.getWithSync(taskYamlKey(core, task.taskId), () -> completeIfAllDone(core, task))) {
                list.add(task);
            }
        }
        return list;
    }

    /**
     * Must be called under the write lock of the task.
     * @return task.completed - after the task was completed, when everything about it is done
     */
    private boolean completeIfAllDone(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, ProcessorCoreTask task) {
        if (!task.completed && task.finishedOn != null && task.reported && task.delivered && task.output.outputStatuses.stream().allMatch(o -> o.uploaded)
                // still in the map - a task deleted after it was found must not be written, save() would create its dir again
                && getTasksForProcessorCore(core).get(task.taskId) == task) {
            task.completed = true;
            save(core, task);
        }
        return task.completed;
    }

    private Map<Long, ProcessorCoreTask> getTasksForDispatcherUrl(ProcessorAndCoreData.DispatcherUrl dispatcherUrl) {
        Map<Long, ProcessorCoreTask> result = new HashMap<>();

        for (Map<DispatcherUrl, Map<Long, ProcessorCoreTask>> value : map.values()) {
            for (Map.Entry<DispatcherUrl, Map<Long, ProcessorCoreTask>> entry : value.entrySet()) {
                if (entry.getKey().equals(dispatcherUrl)) {
                    result.putAll(entry.getValue());
                }
            }
        }
        return result;
    }

    private Map<Long, ProcessorCoreTask> getTasksForProcessorCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        // concurrent maps - there isn't any lock over all tasks, only the lock of each task, see ProcessorTaskSyncService
        return map.computeIfAbsent(core.coreCode, k -> new ConcurrentHashMap<>()).computeIfAbsent(core.dispatcherUrl, m -> new ConcurrentHashMap<>());
    }

    private Map<Long, TaskShortInfo> getAllTasksForProcessorCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        return allTasks.computeIfAbsent(core.dispatcherUrl, k -> new ConcurrentHashMap<>()).computeIfAbsent(core.coreCode, k -> new ConcurrentHashMap<>());
    }

    /**
     * @return every Task of the core with a dir on disk, including the Tasks of FINISHED ExecContexts which weren't loaded at startup
     */
    public List<TaskShortInfo> findAllTaskShortInfosForCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        return List.copyOf(getAllTasksForProcessorCore(core).values());
    }

    public List<ProcessorCoreTask> findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, boolean assetsPreparedStatus) {
        List<ProcessorCoreTask> list = new ArrayList<>();
        Map<Long, ProcessorCoreTask> mapForDispatcherUrl = getTasksForProcessorCore(core);
        List<Long> forDeletion = new ArrayList<>();
        for (ProcessorCoreTask task : mapForDispatcherUrl.values()) {
            // the state of the task - several fields - is read under the lock of the task
            ProcessorTaskSyncService.getWithReadSyncVoid(taskYamlKey(core, task.taskId), () -> {
                if (S.b(task.dispatcherUrl)) {
                    forDeletion.add(task.taskId);
                }
                if (!task.completed && task.finishedOn == null && task.assetsPrepared == assetsPreparedStatus) {
                    list.add(task);
                }
            });
        }
        forDeletion.forEach(id -> {
            log.warn("01.713.480 task #{} from dispatcher {} was deleted from global map with tasks", id, core.dispatcherUrl.url);
            mapForDispatcherUrl.remove(id);
        });
        return list;
    }

    public void addTakToCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, ProcessorCoreTask task) {
        getTasksForProcessorCore(core).put(task.taskId, task);
    }

    @SneakyThrows
    public void createTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherCommParamsYaml.AssignedTask assignedTask) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, assignedTask.taskId));
        try {
            lock.lock();
            processorEnvironment.getProcessorEnv().metadataParams().registerTaskQuota(core.dispatcherUrl.url, assignedTask.taskId, assignedTask.tag, assignedTask.quota);

            log.info("713.500 Prepare new task #{} on core #{}", assignedTask.taskId, core.coreId);
            Map<Long, ProcessorCoreTask> mapForDispatcherUrl = getTasksForProcessorCore(core);
            ProcessorCoreTask task = mapForDispatcherUrl.computeIfAbsent(assignedTask.taskId, k -> new ProcessorCoreTask());

            task.taskId = assignedTask.taskId;
            task.execContextId = assignedTask.execContextId;
            task.params = assignedTask.params;
            task.functionExecResult = null;
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.UTILS.to(assignedTask.params);
            task.clean = taskParamYaml.task.clean;
            task.cleaningPolicy = taskParamYaml.task.function.cleaningPolicy;
            task.dispatcherUrl = core.dispatcherUrl.url;
            task.createdOn = System.currentTimeMillis();
            task.assetsPrepared = false;
            task.launchedOn = null;
            task.finishedOn = null;
            task.reportedOn = null;
            task.reported = false;
            task.delivered = false;
            task.completed = false;
            task.quotas.quota = assignedTask.quota;
            taskParamYaml.task.outputs.stream()
                    .map(o -> new ProcessorCoreTask.OutputStatus(o.id, false))
                    .collect(Collectors.toCollection(() -> task.output.outputStatuses));

            Path coreDir = processorPath.resolve(core.coreCode);
            if (Files.notExists(coreDir)) {
                Files.createDirectories(coreDir);
            }
            Path coreTaskDir = coreDir.resolve(Consts.TASK_DIR);

            Path dispatcherDir = coreTaskDir.resolve(processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core).dispatcherCode);
            String path = DirUtils.getPoweredPath(assignedTask.taskId);
            Path taskDir = dispatcherDir.resolve(path);
            try {
                if (Files.exists(taskDir)) {
                    try {
                        PathUtils.deleteDirectory(taskDir);
                    } catch (IOException e) {
                        String es = "713.520 Error while deleting a task dir: " + taskDir.toAbsolutePath();
                        log.error(es, e);
                        throw new RuntimeException(es, e);
                    }
                }
                Files.createDirectories(taskDir);
                Path taskYamlFile = taskDir.resolve(Consts.TASK_YAML);
                Files.writeString(taskYamlFile, ProcessorTaskUtils.toString(task), StandardCharsets.UTF_8, CREATE, WRITE, TRUNCATE_EXISTING);
                getAllTasksForProcessorCore(core).put(task.taskId, new TaskShortInfo(task.taskId, task.execContextId));
            } catch (Throwable th) {
                String es = "713.540 Error";
                log.error(es, th);
                throw new RuntimeException(es, th);
            }
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public ProcessorCoreTask resetTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(null);
            return save(core, task);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public ProcessorCoreTask setLaunchOn(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(core, task);
        } finally {
            lock.unlock();
        }
    }

    @SneakyThrows
    private ProcessorCoreTask save(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, ProcessorCoreTask task) {
        // a task is changed and written only under its own write lock, see ProcessorTaskSyncService
        ProcessorTaskSyncService.checkWriteLockPresent(taskYamlKey(core, task.taskId));
        Path taskDir = prepareTaskDir(core, task.taskId);
        Path taskYaml = taskDir.resolve(Consts.TASK_YAML);

        final ProcessorCoreTask saved = actualSave(task, taskDir, taskYaml);
        // publishes the changed state to readers which don't lock the task: an update of a ConcurrentHashMap
        // happens-before a retrieval which sees it. replace() never adds again a task which was deleted from the map
        getTasksForProcessorCore(core).replace(task.taskId, task);
        return saved;
    }

    public static ProcessorCoreTask actualSave(ProcessorCoreTask task, Path taskDir, Path taskYaml) throws IOException {
        if (Files.exists(taskYaml)) {
            log.trace("{} file exists. Make backup", taskYaml.toAbsolutePath());
            Path yamlFileBak = taskDir.resolve(Consts.TASK_YAML + ".bak");
            Files.deleteIfExists(yamlFileBak);
            if (Files.exists(taskYaml)) {
                Files.move(taskYaml, yamlFileBak);
            }
        }

        try {
            Files.writeString(taskYaml, ProcessorTaskUtils.toString(task), StandardCharsets.UTF_8, CREATE, WRITE, TRUNCATE_EXISTING, SYNC);
        } catch (IOException e) {
            String es = "713.560 Error while writing to file: " + taskYaml.toAbsolutePath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return task;
    }

    @Nullable
    public ProcessorCoreTask findByIdForCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        return getTasksForProcessorCore(core)
                .entrySet()
                .stream()
                .filter(e -> e.getValue().taskId.equals(taskId))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public List<ProcessorCoreTask> findAllForCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        Collection<ProcessorCoreTask> values = getTasksForProcessorCore(core).values();
        return List.copyOf(values);
    }

    public List<String> findCoreCodesWithTaskId(Long taskId) {
        List<String> codes = new ArrayList<>();
        for (Map.Entry<String, Map<DispatcherUrl, Map<Long, ProcessorCoreTask>>> entry : map.entrySet()) {
            for (Map.Entry<DispatcherUrl, Map<Long, ProcessorCoreTask>> dispatcherUrlMapEntry : entry.getValue().entrySet()) {
                for (Map.Entry<Long, ProcessorCoreTask> longProcessorCoreTaskEntry : dispatcherUrlMapEntry.getValue().entrySet()) {
                    if (longProcessorCoreTaskEntry.getKey().equals(taskId)) {
                        codes.add(entry.getKey());
                    }
                }
            }
        }
        return codes;
    }

    public void delete(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, final Long taskId) {
        MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core);

        final Path processorDir = processorPath.resolve(core.coreCode);
        final Path processorTaskDir = processorDir.resolve(Consts.TASK_DIR);
        final Path dispatcherDir = processorTaskDir.resolve(processorState.dispatcherCode);
        final Path taskDir = DirUtils.getPoweredPath(dispatcherDir, taskId);

        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            processorEnvironment.getProcessorEnv().metadataParams().removeQuota(core.dispatcherUrl.url, taskId);
            try {
                if (Files.exists(taskDir)) {
                    deleteDir(taskDir, "delete dir in ProcessorTaskService.delete()");
                }
                Map<Long, ProcessorCoreTask> mapTask = getTasksForProcessorCore(core);
                if (log.isDebugEnabled()) {
                    log.debug("Does task present in map before deleting: {}", mapTask.containsKey(taskId));
                }
                mapTask.remove(taskId);
                getAllTasksForProcessorCore(core).remove(taskId);
                if (log.isDebugEnabled()) {
                    log.debug("Does task present in map after deleting: {}", mapTask.containsKey(taskId));
                }
            } catch (java.lang.NoClassDefFoundError th) {
                log.error("713.580 Error deleting task {}, {}", taskId, th.getMessage());
            } catch (Throwable th) {
                log.error("713.600 Error deleting task " + taskId, th);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes the 'asset' sub-dir of a task's dir, i.e. the dir which was filled in by TaskProcessor
     * from Function's assetDir. Everything else in taskDir is left as is.
     * Path-based and static on purpose - it's testable without a Spring context.
     *
     * @return true when the dir existed before the call and doesn't exist after it.
     */
    public static boolean deleteTaskAssetDir(Path taskDir) {
        final Path assetDir = taskDir.resolve(ConstsApi.ASSET_DIR);
        if (Files.notExists(assetDir)) {
            return false;
        }
        deleteDir(assetDir, "delete asset dir of task because of cleaningPolicy==ASSETS");
        return Files.notExists(assetDir);
    }

    public void deleteAssetDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, final Long taskId) {
        MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core);

        final Path processorDir = processorPath.resolve(core.coreCode);
        final Path processorTaskDir = processorDir.resolve(Consts.TASK_DIR);
        final Path dispatcherDir = processorTaskDir.resolve(processorState.dispatcherCode);
        final Path taskDir = DirUtils.getPoweredPath(dispatcherDir, taskId);

        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLockNotHeld(taskYamlKey(core, taskId));
        try {
            lock.lock();
            if (deleteTaskAssetDir(taskDir)) {
                log.info("01.713.640 asset dir of task #{} was deleted, url {}", taskId, core.dispatcherUrl.url);
            }
        } catch (Throwable th) {
            log.error("01.713.660 Error deleting asset dir of task " + taskId, th);
        } finally {
            lock.unlock();
        }
    }

    @SneakyThrows
    public Path prepareTaskDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        final Path processorDir = processorPath.resolve(core.coreCode);
        final Path processorTaskDir = processorDir.resolve(Consts.TASK_DIR);
        final Path dispatcherDir = processorTaskDir.resolve(MetadataParams.asCode(core.dispatcherUrl));
        Path taskDir = DirUtils.getPoweredPath(dispatcherDir, taskId);
        if (Files.exists(taskDir)) {
            return taskDir;
        }
        Files.createDirectories(taskDir);
        return taskDir;
    }

    @SneakyThrows
    @Nullable
    public static Path prepareTaskSubDir(Path taskDir, String subDir) {
        Path taskSubDir = taskDir.resolve(subDir);
        Files.createDirectories(taskSubDir);
        if (Files.notExists(taskSubDir)) {
            log.warn("713.620 Can't create taskSubDir: {}", taskSubDir.toAbsolutePath());
            return null;
        }
        return taskSubDir;
    }

}
