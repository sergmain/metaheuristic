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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.utils.DigitUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTaskUtils;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

@SuppressWarnings({"WeakerAccess", "DuplicatedCode"})
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class ProcessorTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final MetadataService metadataService;

    /**key - code of core
     * value:
     *      Map of:
     *      key - DispatcherUrl
     *      value:
     *             Map.of:
     *             key - ProcessorCoreTask.taskId,
     *             Value - ProcessorCoreTask
     */
    private final ConcurrentHashMap<String, Map<DispatcherUrl, Map<Long, ProcessorCoreTask>>> map = new ConcurrentHashMap<>();

    @PostConstruct
    public void postConstruct() {
        if (globals.testing) {
            return;
        }
        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : metadataService.getAllEnabledRefsForCores()) {

            File processorDir = new File(globals.processor.dir.dir, core.coreCode);
            File processorTaskDir = new File(processorDir, Consts.TASK_DIR);
            String dispatcherCode = MetadataService.asCode(core.dispatcherUrl);
            File dispatcherDir = new File(processorTaskDir, dispatcherCode);
            if (!dispatcherDir.exists()) {
                dispatcherDir.mkdirs();
            }

            try {
                DispatcherUrl dispatcherUrl = core.dispatcherUrl;

                // !!! do not remove try(Stream<Path>){}
                try (final Stream<Path> pathStream = Files.list(dispatcherDir.toPath())) {
                    pathStream.forEach(p -> {
                        final File taskGroupDir = p.toFile();
                        if (!taskGroupDir.isDirectory()) {
                            return;
                        }
                        try {
                            AtomicBoolean isEmpty = new AtomicBoolean(true);
                            try (final Stream<Path> stream = Files.list(p)) {
                                stream.forEach(s -> {
                                    isEmpty.set(false);
                                    String groupDirName = taskGroupDir.getName();
                                    final File currDir = s.toFile();
                                    String name = currDir.getName();
                                    long taskId = Long.parseLong(groupDirName) * DigitUtils.DIV + Long.parseLong(name);
                                    log.info("Found dir of task with id: {}, {}, {}, {}", taskId, groupDirName, name, dispatcherUrl.url);
                                    File taskYamlFile = new File(currDir, Consts.TASK_YAML);
                                    if (!taskYamlFile.exists() || taskYamlFile.length() == 0L) {
                                        deleteDir(currDir, "Delete not valid dir of task " + s + ", exist: " + taskYamlFile.exists() + ", length: " + taskYamlFile.length());
                                        return;
                                    }

                                    try (FileInputStream fis = new FileInputStream(taskYamlFile)) {
                                        ProcessorCoreTask task = ProcessorTaskUtils.to(fis);
                                        if (S.b(task.dispatcherUrl)) {
                                            deleteDir(currDir, "#713.005 Delete not valid dir of task " + s);
                                            log.warn("#713.007 task #{} from dispatcher {} was deleted from disk because dispatcherUrl field was empty", taskId, dispatcherUrl);
                                            return;
                                        }
                                        getTasksForProcessorCore(core).put(taskId, task);

                                        // fix state of task
                                        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResult());
                                        if (functionExec != null &&
                                                ((functionExec.generalExec != null && !functionExec.exec.isOk) ||
                                                        (functionExec.generalExec != null && !functionExec.generalExec.isOk))) {
                                            markAsFinished(core, taskId, functionExec);
                                        }
                                    }
                                    catch (IOException e) {
                                        String es = "#713.010 Error";
                                        log.error(es, e);
                                        throw new RuntimeException(es, e);
                                    }
                                    catch (YAMLException e) {
                                        String es = "#713.020 yaml Error: " + e.getMessage();
                                        log.warn(es, e);
                                        deleteDir(currDir, "Delete not valid dir of task " + s);
                                    }
                                });
                            }
                        }
                        catch (IOException e) {
                            String es = "#713.030 Error";
                            log.error(es, e);
                            throw new RuntimeException(es, e);
                        }
                    });
                }
            } catch (IOException e) {
                String es = "#713.040 Error";
                log.error(es, e);
                throw new RuntimeException(es, e);
            }
        }
        //noinspection unused
        int i=0;
    }

    public static void deleteDir(@NonNull File f, @NonNull String info) {
        log.warn(info+", file: " + f.getAbsolutePath());
        try {
            if (f.exists()) {
                FileUtils.deleteDirectory(f);
            }
        } catch (IOException e) {
            log.warn("#713.060 Error while deleting dir {}, error: {}", f.getPath(), e.getMessage());
        }
    }

    public void setReportedOn(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.065 setReportedOn({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.070 ProcessorCoreTask wasn't found for Id " + taskId);
                return;
            }
            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(core, task);
        }
    }

    public void setInputAsEmpty(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId, String variableId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.075 setInputAsEmpty({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.077 ProcessorCoreTask wasn't found for Id " + taskId);
                return;
            }
            final ProcessorCoreTask.EmptyStateOfInput input = task.empty.empties.stream().filter(o -> o.variableId.equals(variableId)).findFirst().orElse(null);
            if (input==null) {
                task.empty.empties.add(new ProcessorCoreTask.EmptyStateOfInput(variableId, true));
            }
            else {
                input.empty = true;
            }

            // TODO P0 2022-04-27 is that bug or what's meaning of this?
//            task.setReported(true);
//            task.setReportedOn(System.currentTimeMillis());

            save(core, task);
        }
    }

    public void setDelivered(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.080 setDelivered({}, {})", core.dispatcherUrl.url, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.090 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            if (!task.isReported() ) {
                log.warn("#713.095 This state need to be investigated, task wasn't reported to dispatcher");
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
                if (functionExec!=null && !functionExec.allFunctionsAreOk()) {
                    task.setCompleted(true);
                }
                else {
                    task.setCompleted(task.output.allUploaded());
                }
            }
            save(core, task);
        }
    }

    public void setVariableUploadedAndCompleted(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, Long outputVariableId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("setResourceUploadedAndCompleted({}, {}, {})", core.dispatcherUrl, taskId, outputVariableId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.090 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            task.output.outputStatuses.stream().filter(o -> o.variableId.equals(outputVariableId)).findFirst().ifPresent(status -> status.uploaded = true);
            task.setCompleted( task.isDelivered() );
            save(core, task);
        }
    }

    @SuppressWarnings("unused")
    public void setCompleted(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("setCompleted({}, {})", core.dispatcherUrl, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.100 ProcessorCoreTask wasn't found for Id {}", taskId);
                return;
            }
            task.setCompleted(true);
            save(core, task);
        }
    }

    public List<ProcessorCoreTask> getForReporting(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            Stream<ProcessorCoreTask> stream = findAllByFinishedOnIsNotNull(core);
            List<ProcessorCoreTask> result = stream
                    .filter(processorTask -> !processorTask.isReported() ||
                            (!processorTask.isDelivered() &&
                                    (processorTask.getReportedOn() == null || (System.currentTimeMillis() - processorTask.getReportedOn()) > 60_000)))
                    .collect(Collectors.toList());
            return result;
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
            if (task.isDelivered() && !task.isReported() ) {
                log.warn("#713.105 This state need to be investigated: (task.isDelivered() && !task.isReported())==true");
            }
            // TODO 2019-07-12 do we need to check against task.isReported()? isn't task.isDelivered() just enough?
            //  2020-09-26 until #713.105 (task.isDelivered() && !task.isReported() ) will be fixed, this check is correct
            //  2020-11-23 looks like #713.105 can occur in case when a task was finished with status ERROR
            if (task.isDelivered() && task.isReported() ) {
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
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            markAsFinished(core, taskId,
                    new FunctionApiData.FunctionExec(
                            null, null, null,
                            new FunctionApiData.SystemExecResult("system-error", false, -992, es)));
        }
    }

    void markAsFinished(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, FunctionApiData.FunctionExec functionExec) {

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("markAsFinished({}, #{}, {})", core.dispatcherUrl.url, taskId, functionExec);

            metadataService.removeQuota(core.dispatcherUrl.url, taskId);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.110 ProcessorCoreTask wasn't found for Id #" + taskId);
            } else {
                if (task.getLaunchedOn()==null) {
                    final TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                    log.info("#713.113 task #{}, function '{}', doesn't have the launchedOn as inited", taskId, tpy.task.function.code);
                    final String es = "#713.114 stacktrace";
                    try {
                        throw new RuntimeException(es);
                    } catch (RuntimeException e) {
                        log.info(es, e);
                    }
                    task.setLaunchedOn(System.currentTimeMillis());
                }
                if (!functionExec.allFunctionsAreOk()) {
                    log.info("#713.115 task #{} was finished with an error, set completed to true", taskId);
                    task.setCompleted(true);
                }
                else {
                    task.setCompleted(false);
                }
                task.setFinishedOn(System.currentTimeMillis());
                task.setReported(false);
                task.setFunctionExecResult(FunctionExecUtils.toString(functionExec));

                save(core, task);
            }
        }
    }

    void markAsAssetPrepared(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, boolean status) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("markAsAssetPrepared(dispatcherUrl: {}, taskId: {}, status: {})", core.dispatcherUrl, taskId, status);
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                log.error("#713.130 ProcessorCoreTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(status);
                save(core, task);
            }
        }
    }

    boolean isNeedNewTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
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
    }

    public List<ProcessorCoreTask> findAllByCompletedIsFalse(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            List<ProcessorCoreTask> list = new ArrayList<>();
            for (ProcessorCoreTask task : getTasksForProcessorCore(core).values()) {
                if (!task.completed) {
                    if (task.finishedOn!=null && task.reported && task.delivered && task.output.outputStatuses.stream().allMatch(o->o.uploaded)) {
                        task.completed = true;
                        save(core, task);
                    }
                    else {
                        list.add(task);
                    }
                }
            }
            return list;
        }
    }

    private Map<Long, ProcessorCoreTask> getTasksForDispatcherUrl(ProcessorAndCoreData.DispatcherUrl dispatcherUrl) {
        Map<Long, ProcessorCoreTask> result = new HashMap<>();

        for (Map<DispatcherUrl, Map<Long, ProcessorCoreTask>> value : map.values()) {
            for (Map.Entry<DispatcherUrl, Map<Long, ProcessorCoreTask>> entry : value.entrySet()) {
                if (entry.getKey().equals(dispatcherUrl)) {
                    result.putAll( entry.getValue());
                }
            }
        }
        return result;
    }

    private Map<Long, ProcessorCoreTask> getTasksForProcessorCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        return map.computeIfAbsent(core.coreCode, k->new HashMap<>()).computeIfAbsent(core.dispatcherUrl, m -> new HashMap<>());
    }

    public List<ProcessorCoreTask> findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, boolean assetsPreparedStatus) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            List<ProcessorCoreTask> list = new ArrayList<>();
            Map<Long, ProcessorCoreTask> mapForDispatcherUrl = getTasksForProcessorCore(core);
            List<Long> forDeletion = new ArrayList<>();
            for (ProcessorCoreTask task : mapForDispatcherUrl.values()) {
                if (S.b(task.dispatcherUrl)) {
                    forDeletion.add(task.taskId);
                }
                if (!task.completed && task.finishedOn == null && task.assetsPrepared==assetsPreparedStatus) {
                    list.add(task);
                }
            }
            forDeletion.forEach(id-> {
                log.warn("#713.147 task #{} from dispatcher {} was deleted from global map with tasks", id, core.dispatcherUrl.url);
                mapForDispatcherUrl.remove(id);
            });
            return list;
        }
    }

    private Stream<ProcessorCoreTask> findAllByFinishedOnIsNotNull(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        return getTasksForProcessorCore(core).values().stream().filter(o -> o.finishedOn!=null);
    }

    public void createTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherCommParamsYaml.AssignedTask assignedTask) {

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            metadataService.registerTaskQuota(core.dispatcherUrl.url, assignedTask.taskId, assignedTask.tag, assignedTask.quota);

            log.info("#713.150 Prepare new task #{} on core #{}", assignedTask.taskId, core.coreId);
            Map<Long, ProcessorCoreTask> mapForDispatcherUrl = getTasksForProcessorCore(core);
            ProcessorCoreTask task = mapForDispatcherUrl.computeIfAbsent(assignedTask.taskId, k -> new ProcessorCoreTask());

            task.taskId = assignedTask.taskId;
            task.execContextId = assignedTask.execContextId;
            task.params = assignedTask.params;
            task.functionExecResult = null;
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(assignedTask.params);
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
                    .map(o->new ProcessorCoreTask.OutputStatus(o.id, false) )
                    .collect(Collectors.toCollection(()->task.output.outputStatuses));

            File processorDir = new File(globals.processor.dir.dir, core.coreCode);
            if (!processorDir.exists()) {
                processorDir.mkdirs();
            }
            File processorTaskDir = new File(processorDir, Consts.TASK_DIR);

            File dispatcherDir = new File(processorTaskDir, metadataService.processorStateByDispatcherUrl(core).dispatcherCode);
            String path = getTaskPath(assignedTask.taskId);
            File taskDir = new File(dispatcherDir, path);
            try {
                if (taskDir.exists()) {
                    try {
                        FileUtils.deleteDirectory(taskDir);
                    }
                    catch (IOException e) {
                        String es = "#713.140 Error while deleting a task dir: " + taskDir.getAbsolutePath();
                        log.error(es, e);
                        throw new RuntimeException(es, e);
                    }
                }
                taskDir.mkdirs();
                //noinspection ResultOfMethodCallIgnored
                taskDir.mkdirs();
                File taskYamlFile = new File(taskDir, Consts.TASK_YAML);
                FileUtils.write(taskYamlFile, ProcessorTaskUtils.toString(task), StandardCharsets.UTF_8, false);
            } catch (Throwable th) {
                String es = "#713.160 Error";
                log.error(es, th);
                throw new RuntimeException(es, th);
            }
        }
    }

    @Nullable
    public ProcessorCoreTask resetTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(null);
            return save(core, task);
        }
    }

    @Nullable
    public ProcessorCoreTask setLaunchOn(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            ProcessorCoreTask task = findByIdForCore(core, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(core, task);
        }
    }

    private ProcessorCoreTask save(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, ProcessorCoreTask task) {
        File taskDir = prepareTaskDir(core, task.taskId);
        File taskYaml = new File(taskDir, Consts.TASK_YAML);

        if (taskYaml.exists()) {
            log.trace("{} file exists. Make backup", taskYaml.getPath());
            File yamlFileBak = new File(taskDir, Consts.TASK_YAML + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (taskYaml.exists()) {
                //noinspection ResultOfMethodCallIgnored
                taskYaml.renameTo(yamlFileBak);
            }
        }

        try {
            FileUtils.write(taskYaml, ProcessorTaskUtils.toString(task), StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            String es = "#713.200 Error while writing to file: " + taskYaml.getPath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return task;
    }

    @Nullable
    public ProcessorCoreTask findByIdForCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            return getTasksForProcessorCore(core)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().taskId.equals(taskId))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(null);
        }
    }

    public List<ProcessorCoreTask> findAllForCore(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            Collection<ProcessorCoreTask> values = getTasksForProcessorCore(core).values();
            return List.copyOf(values);
        }
    }

    @Nullable
    public String findCoreCodeWithTaskId(Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            for (Map.Entry<String, Map<DispatcherUrl, Map<Long, ProcessorCoreTask>>> entry : map.entrySet()) {
                for (Map.Entry<DispatcherUrl, Map<Long, ProcessorCoreTask>> dispatcherUrlMapEntry : entry.getValue().entrySet()) {
                    for (Map.Entry<Long, ProcessorCoreTask> longProcessorCoreTaskEntry : dispatcherUrlMapEntry.getValue().entrySet()) {
                        if (longProcessorCoreTaskEntry.getKey().equals(taskId)) {
                            return entry.getKey();
                        }
                    }
                }
            }
        }
        return null;
    }

    public void delete(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, final Long taskId) {
        MetadataParamsYaml.ProcessorSession processorState = metadataService.processorStateByDispatcherUrl(core);

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            metadataService.removeQuota(core.dispatcherUrl.url, taskId);
            final File processorDir = new File(globals.processor.dir.dir, core.coreCode);
            final File processorTaskDir = new File(processorDir, Consts.TASK_DIR);
            final File dispatcherDir = new File(processorTaskDir, processorState.dispatcherCode);

            final String path = getTaskPath(taskId);
            final File taskDir = new File(dispatcherDir, path);
            try {
                if (taskDir.exists()) {
                    deleteDir(taskDir, "delete dir in ProcessorTaskService.delete()");
                }
                Map<Long, ProcessorCoreTask> mapTask = getTasksForProcessorCore(core);
                log.debug("Does task present in map before deleting: {}", mapTask.containsKey(taskId));
                mapTask.remove(taskId);
                log.debug("Does task present in map after deleting: {}", mapTask.containsKey(taskId));
            }
            catch (java.lang.NoClassDefFoundError th) {
                log.error("#713.205 Error deleting task {}, {}", taskId, th.getMessage());
            }
            catch (Throwable th) {
                log.error("#713.210 Error deleting task " + taskId, th);
            }
        }
    }

    private static String getTaskPath(long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        return ""+power.power7+File.separatorChar+power.power4+File.separatorChar;
    }

    public File prepareTaskDir(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        final File processorDir = new File(globals.processor.dir.dir, core.coreCode);
        final File processorTaskDir = new File(processorDir, Consts.TASK_DIR);
        final File dispatcherDir = new File(processorTaskDir, MetadataService.asCode(core.dispatcherUrl));
        File taskDir = new File(dispatcherDir, getTaskPath(taskId));
        if (taskDir.exists()) {
            return taskDir;
        }
        //noinspection unused
        boolean status = taskDir.mkdirs();
        return taskDir;
    }

    @Nullable
    public static File prepareTaskSubDir(File taskDir, String subDir) {
        File taskSubDir = new File(taskDir, subDir);
        //noinspection ResultOfMethodCallIgnored
        taskSubDir.mkdirs();
        if (!taskSubDir.exists()) {
            log.warn("#713.220 Can't create taskSubDir: {}", taskSubDir.getAbsolutePath());
            return null;
        }
        return taskSubDir;
    }

}
