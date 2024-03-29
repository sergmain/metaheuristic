/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import org.springframework.lang.Nullable;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;
import static java.nio.file.StandardOpenOption.*;

@SuppressWarnings({"WeakerAccess", "DuplicatedCode"})
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final ProcessorEnvironment processorEnvironment;

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

    public Path processorPath;

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
        final LinkedList<CompletableFuture<Void>> list = new LinkedList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.metadataParams.getAllEnabledRefsForCores()) {

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
                                    stream.map(taskDir -> CompletableFuture.supplyAsync(() -> processTaskDir(core, taskDir, taskGroupDir, dispatcherUrl), executor))
                                        .collect(Collectors.toCollection(() -> list));
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
            CompletableFuture.allOf(list.toArray(CompletableFuture[]::new)).join();
        }
    }

    private Void processTaskDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, Path taskGroupDir, DispatcherUrl dispatcherUrl) {
        try {
            if (!Thread.currentThread().isVirtual()) {
                throw new IllegalStateException("(!Thread.currentThread().isVirtual())");
            }
            String groupDirName = taskGroupDir.getFileName().toString();
            String name = taskDir.getFileName().toString();
            long taskId = Long.parseLong(groupDirName) * DigitUtils.DIV + Long.parseLong(name);
            log.info("Found dir of task with id: {}, {}, {}, {}", taskId, groupDirName, name, dispatcherUrl.url);
            Path taskYamlFile = taskDir.resolve(Consts.TASK_YAML);
            if (Files.notExists(taskYamlFile) || Files.size(taskYamlFile) == 0L) {
                deleteDir(taskDir, "Delete not valid dir of task " + taskDir + ", exist: " + Files.exists(taskYamlFile) + ", length: " + Files.size(taskYamlFile));
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
                    throw new IllegalStateException("713.120 (task.taskId==null || taskId!=task.taskId)");
                }
                addTakToCore(core, task);

                // fix state of task
                FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResult());
                if (functionExec != null &&
                        ((functionExec.generalExec != null && !functionExec.exec.isOk) ||
                                (functionExec.generalExec != null && !functionExec.generalExec.isOk))) {
                    markAsFinished(core, taskId, functionExec);
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

    public static void deleteDir(Path f, String info) {
        log.warn(info + ", file: " + f.toAbsolutePath());
        try {
            PathUtils.deleteDirectory(f);
        } catch (IOException e) {
            log.warn("713.180 Error while deleting dir {}, error: {}", f, e.toString());
        }
    }

    public void setReportedOn(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        try {
            ProcessorSyncHolder.writeLock.lock();
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
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    public void setInputAsEmpty(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId, String variableId) {
        try {
            ProcessorSyncHolder.writeLock.lock();
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
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    public void setDelivered(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        try {
            ProcessorSyncHolder.writeLock.lock();

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
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    public void setVariableUploadedAndCompleted(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, Long outputVariableId) {
        try {
            ProcessorSyncHolder.writeLock.lock();
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
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    @SuppressWarnings("unused")
    public void setCompleted(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            log.info("setCompleted({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.360 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            task.setCompleted(true);
            save(core, task);
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    public List<ProcessorCoreTask> getForReporting(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        try {
            ProcessorSyncHolder.readLock.lock();
            List<ProcessorCoreTask> result = getTasksForProcessorCore(core).values().stream()
                .filter(o -> o.finishedOn != null)
                .filter(processorTask -> !processorTask.isReported() ||
                    (!processorTask.isDelivered() &&
                        (processorTask.getReportedOn() == null || (System.currentTimeMillis() - processorTask.getReportedOn()) > 60_000)))
                .collect(Collectors.toList());
            return result;
        } finally {
            ProcessorSyncHolder.readLock.unlock();
        }
    }

    @Nullable
    public ProcessorCommParamsYaml.ReportTaskProcessingResult reportTaskProcessingResult(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        final List<ProcessorCoreTask> list = getForReporting(core);
        if (list.isEmpty()) {
            return null;
        }
        log.info("Number of tasks for reporting: " + list.size());
        final ProcessorCommParamsYaml.ReportTaskProcessingResult processingResult = new ProcessorCommParamsYaml.ReportTaskProcessingResult();
        for (ProcessorCoreTask task : list) {
            if (task.isDelivered() && !task.isReported()) {
                log.warn("713.380 This state need to be investigated: (task.isDelivered() && !task.isReported())==true");
            }
            // TODO 2019-07-12 do we need to check against task.isReported()? isn't task.isDelivered() just enough?
            //  2020-09-26 until 713.105 (task.isDelivered() && !task.isReported() ) will be fixed, this check is correct
            //  2020-11-23 looks like 713.105 can occur in case when a task was finished with status ERROR
            if (task.isDelivered() && task.isReported()) {
                continue;
            }
            final ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result =
                    new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getTaskId(), task.getFunctionExecResult());
            processingResult.results.add(result);
            setReportedOn(core, task.taskId);
        }
        return processingResult;
    }

    public void markAsFinishedWithError(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId, String es) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            markAsFinished(core, taskId,
                    new FunctionApiData.FunctionExec(
                            null, null, null,
                            new FunctionApiData.SystemExecResult("system-error", false, -992, es)));
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    void markAsFinished(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, FunctionApiData.FunctionExec functionExec) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            log.info("markAsFinished({}, #{}, {})", core.dispatcherUrl.url, taskId, functionExec);

            processorEnvironment.metadataParams.removeQuota(core.dispatcherUrl.url, taskId);
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
                task.setFunctionExecResult(FunctionExecUtils.toString(functionExec));

                save(core, task);
            }
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    void markAsAssetPrepared(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, boolean status) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            log.info("markAsAssetPrepared(dispatcherUrl: {}, taskId: {}, status: {})", core.dispatcherUrl, taskId, status);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("713.460 ProcessorCoreTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(status);
                save(core, task);
            }
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
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
        try {
            ProcessorSyncHolder.writeLock.lock();
            List<ProcessorCoreTask> list = new ArrayList<>();
            for (ProcessorCoreTask task : getTasksForProcessorCore(core).values()) {
                if (!task.completed) {
                    if (task.finishedOn != null && task.reported && task.delivered && task.output.outputStatuses.stream().allMatch(o -> o.uploaded)) {
                        task.completed = true;
                        save(core, task);
                    } else {
                        list.add(task);
                    }
                }
            }
            return list;
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
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
        return map.computeIfAbsent(core.coreCode, k -> new HashMap<>()).computeIfAbsent(core.dispatcherUrl, m -> new HashMap<>());
    }

    public List<ProcessorCoreTask> findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, boolean assetsPreparedStatus) {
        try {
            ProcessorSyncHolder.writeLock.lock();

            List<ProcessorCoreTask> list = new ArrayList<>();
            Map<Long, ProcessorCoreTask> mapForDispatcherUrl = getTasksForProcessorCore(core);
            List<Long> forDeletion = new ArrayList<>();
            for (ProcessorCoreTask task : mapForDispatcherUrl.values()) {
                if (S.b(task.dispatcherUrl)) {
                    forDeletion.add(task.taskId);
                }
                if (!task.completed && task.finishedOn == null && task.assetsPrepared == assetsPreparedStatus) {
                    list.add(task);
                }
            }
            forDeletion.forEach(id -> {
                log.warn("713.480 task #{} from dispatcher {} was deleted from global map with tasks", id, core.dispatcherUrl.url);
                mapForDispatcherUrl.remove(id);
            });
            return list;
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    public void addTakToCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, ProcessorCoreTask task) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            getTasksForProcessorCore(core).put(task.taskId, task);
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    @SneakyThrows
    public void createTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherCommParamsYaml.AssignedTask assignedTask) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            processorEnvironment.metadataParams.registerTaskQuota(core.dispatcherUrl.url, assignedTask.taskId, assignedTask.tag, assignedTask.quota);

            log.info("713.500 Prepare new task #{} on core #{}", assignedTask.taskId, core.coreId);
            Map<Long, ProcessorCoreTask> mapForDispatcherUrl = getTasksForProcessorCore(core);
            ProcessorCoreTask task = mapForDispatcherUrl.computeIfAbsent(assignedTask.taskId, k -> new ProcessorCoreTask());

            task.taskId = assignedTask.taskId;
            task.execContextId = assignedTask.execContextId;
            task.params = assignedTask.params;
            task.functionExecResult = null;
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.UTILS.to(assignedTask.params);
            task.clean = taskParamYaml.task.clean;
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

            Path dispatcherDir = coreTaskDir.resolve(processorEnvironment.metadataParams.processorStateByDispatcherUrl(core).dispatcherCode);
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
            } catch (Throwable th) {
                String es = "713.540 Error";
                log.error(es, th);
                throw new RuntimeException(es, th);
            }
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    @Nullable
    public ProcessorCoreTask resetTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(null);
            return save(core, task);
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    @Nullable
    public ProcessorCoreTask setLaunchOn(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        try {
            ProcessorSyncHolder.writeLock.lock();
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(core, task);
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
        }
    }

    @SneakyThrows
    private ProcessorCoreTask save(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, ProcessorCoreTask task) {
        Path taskDir = prepareTaskDir(core, task.taskId);
        Path taskYaml = taskDir.resolve(Consts.TASK_YAML);

        return actualSave(task, taskDir, taskYaml);
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
        try {
            ProcessorSyncHolder.readLock.lock();

            return getTasksForProcessorCore(core)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().taskId.equals(taskId))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(null);
        } finally {
            ProcessorSyncHolder.readLock.unlock();
        }
    }

    public List<ProcessorCoreTask> findAllForCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        try {
            ProcessorSyncHolder.readLock.lock();

            Collection<ProcessorCoreTask> values = getTasksForProcessorCore(core).values();
            return List.copyOf(values);
        } finally {
            ProcessorSyncHolder.readLock.unlock();
        }
    }

    public List<String> findCoreCodesWithTaskId(Long taskId) {
        List<String> codes = new ArrayList<>();
        try {
            ProcessorSyncHolder.readLock.lock();
            for (Map.Entry<String, Map<DispatcherUrl, Map<Long, ProcessorCoreTask>>> entry : map.entrySet()) {
                for (Map.Entry<DispatcherUrl, Map<Long, ProcessorCoreTask>> dispatcherUrlMapEntry : entry.getValue().entrySet()) {
                    for (Map.Entry<Long, ProcessorCoreTask> longProcessorCoreTaskEntry : dispatcherUrlMapEntry.getValue().entrySet()) {
                        if (longProcessorCoreTaskEntry.getKey().equals(taskId)) {
                            codes.add(entry.getKey());
                        }
                    }
                }
            }

        } finally {
            ProcessorSyncHolder.readLock.unlock();
        }
        return codes;
    }

    public void delete(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, final Long taskId) {
        MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.metadataParams.processorStateByDispatcherUrl(core);

        final Path processorDir = processorPath.resolve(core.coreCode);
        final Path processorTaskDir = processorDir.resolve(Consts.TASK_DIR);
        final Path dispatcherDir = processorTaskDir.resolve(processorState.dispatcherCode);
        final Path taskDir = DirUtils.getPoweredPath(dispatcherDir, taskId);

        try {
            ProcessorSyncHolder.writeLock.lock();
            processorEnvironment.metadataParams.removeQuota(core.dispatcherUrl.url, taskId);
            try {
                if (Files.exists(taskDir)) {
                    deleteDir(taskDir, "delete dir in ProcessorTaskService.delete()");
                }
                Map<Long, ProcessorCoreTask> mapTask = getTasksForProcessorCore(core);
                if (log.isDebugEnabled()) {
                    log.debug("Does task present in map before deleting: {}", mapTask.containsKey(taskId));
                }
                mapTask.remove(taskId);
                if (log.isDebugEnabled()) {
                    log.debug("Does task present in map after deleting: {}", mapTask.containsKey(taskId));
                }
            } catch (java.lang.NoClassDefFoundError th) {
                log.error("713.580 Error deleting task {}, {}", taskId, th.getMessage());
            } catch (Throwable th) {
                log.error("713.600 Error deleting task " + taskId, th);
            }
        } finally {
            ProcessorSyncHolder.writeLock.unlock();
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
