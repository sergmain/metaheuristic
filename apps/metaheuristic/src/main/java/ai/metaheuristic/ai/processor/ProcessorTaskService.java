/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.utils.DigitUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTaskUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

@SuppressWarnings({"WeakerAccess", "DuplicatedCode"})
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
@DependsOn({"Globals", "MetadataService"})
public class ProcessorTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final MetadataService metadataService;
    private final EnvService envService;

    private final Map<DispatcherUrl, Map<Long, ProcessorTask>> map = new ConcurrentHashMap<>();

    @PostConstruct
    public void postConstruct() {
        if (globals.isUnitTesting) {
            return;
        }
        try {
            for (String processorCode : metadataService.getProcessorCodes()) {
                File processorDir = new File(globals.processorDir, processorCode);
                if (!processorDir.exists()) {
                    processorDir.mkdirs();
                }
                File processorTaskDir = new File(processorDir, Consts.TASK_DIR);
                Files.list(processorTaskDir.toPath()).forEach(top -> {
                    try {
                        DispatcherUrl dispatcherUrl = metadataService.findDispatcherByCode(processorCode, top.toFile().getName());
                        if (dispatcherUrl==null) {
                            return;
                        }
                        Files.list(top).forEach(p -> {
                            final File taskGroupDir = p.toFile();
                            if (!taskGroupDir.isDirectory()) {
                                return;
                            }
                            try {
                                AtomicBoolean isEmpty = new AtomicBoolean(true);
                                Files.list(p).forEach(s -> {
                                    isEmpty.set(false);
                                    String groupDirName = taskGroupDir.getName();
                                    final File currDir = s.toFile();
                                    String name = currDir.getName();
                                    long taskId = Long.parseLong(groupDirName) * DigitUtils.DIV + Long.parseLong(name);
                                    log.info("Found dir of task with id: {}, {}, {}", taskId, groupDirName, name);
                                    File taskYamlFile = new File(currDir, Consts.TASK_YAML);
                                    if (!taskYamlFile.exists() || taskYamlFile.length()==0L) {
                                        deleteDir(currDir, "Delete not valid dir of task " + s+", exist: "+taskYamlFile.exists()+", length: " +taskYamlFile.length());
                                        return;
                                    }

                                    try(FileInputStream fis = new FileInputStream(taskYamlFile)) {
                                        ProcessorTask task = ProcessorTaskUtils.to(fis);
                                        if (S.b(task.dispatcherUrl)) {
                                            deleteDir(currDir, "#713.005 Delete not valid dir of task " + s);
                                            log.warn("#713.007 task #{} from dispatcher {} was deleted from disk because dispatcherUrl field was empty", taskId, dispatcherUrl);
                                            return;
                                        }
                                        getMapForDispatcherUrl(dispatcherUrl).put(taskId, task);

                                        // fix state of task
                                        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResult());
                                        if (functionExec !=null &&
                                                ((functionExec.generalExec!=null && !functionExec.exec.isOk ) ||
                                                        (functionExec.generalExec!=null && !functionExec.generalExec.isOk))) {
                                            markAsFinished(processorCode, dispatcherUrl, taskId, functionExec);
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
                            catch (IOException e) {
                                String es = "#713.030 Error";
                                log.error(es, e);
                                throw new RuntimeException(es, e);
                            }
                        });
                    } catch (IOException e) {
                        String es = "#713.040 Error";
                        log.error(es, e);
                        throw new RuntimeException(es, e);
                    }
                });
            }
        }
        catch (IOException e) {
            String es = "#713.050 Error";
            log.error(es, e);
            throw new RuntimeException(es, e);
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
            log.warn("#713.060 Error while deleting dir {}, error: {}", f.getPath(), e.toString());
        }
    }

    public void setReportedOn(String processorCode, DispatcherUrl dispatcherUrl, long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.065 setReportedOn({}, {})", dispatcherUrl, taskId);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.070 ProcessorTask wasn't found for Id " + taskId);
                return;
            }
            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(processorCode, task);
        }
    }

    public void setInputAsEmpty(String processorCode, DispatcherUrl dispatcherUrl, long taskId, String variableId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.075 setInputAsEmpty({}, {})", dispatcherUrl, taskId);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.077 ProcessorTask wasn't found for Id " + taskId);
                return;
            }
            final ProcessorTask.EmptyStateOfInput input = task.empty.empties.stream().filter(o -> o.variableId.equals(variableId)).findFirst().orElse(null);
            if (input==null) {
                task.empty.empties.add(new ProcessorTask.EmptyStateOfInput(variableId, true));
            }
            else {
                input.empty = true;
            }

            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(processorCode, task);
        }
    }

    public void setDelivered(String processorCode, DispatcherUrl dispatcherUrl, Long taskId) {
        if (true) throw new NotImplementedException("add processorCode");

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.080 setDelivered({}, {})", dispatcherUrl, taskId);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.090 ProcessorTask wasn't found for Id {}", taskId);
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
            save(processorCode, task);
        }
    }

    public void setVariableUploadedAndCompleted(String processorCode, DispatcherUrl dispatcherUrl, Long taskId, Long outputVariableId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("setResourceUploadedAndCompleted({}, {}, {})", dispatcherUrl, taskId, outputVariableId);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.090 ProcessorTask wasn't found for Id {}", taskId);
                return;
            }
            task.output.outputStatuses.stream().filter(o -> o.variableId.equals(outputVariableId)).findFirst().ifPresent(status -> status.uploaded = true);
            task.setCompleted( task.isDelivered() );
            save(processorCode, task);
        }
    }

    @SuppressWarnings("unused")
    public void setCompleted(String processorCode, DispatcherUrl dispatcherUrl, Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("setCompleted({}, {})", dispatcherUrl, taskId);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.100 ProcessorTask wasn't found for Id {}", taskId);
                return;
            }
            task.setCompleted(true);
            save(processorCode, task);
        }
    }

    public List<ProcessorTask> getForReporting(DispatcherUrl dispatcherUrl) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            Stream<ProcessorTask> stream = findAllByFinishedOnIsNotNull(dispatcherUrl);
            List<ProcessorTask> result = stream
                    .filter(processorTask -> !processorTask.isReported() ||
                            (!processorTask.isDelivered() &&
                                    (processorTask.getReportedOn() == null || (System.currentTimeMillis() - processorTask.getReportedOn()) > 60_000)))
                    .collect(Collectors.toList());
            return result;
        }
    }

    @Nullable
    public ProcessorCommParamsYaml.ReportTaskProcessingResult reportTaskProcessingResult(String processorCode, DispatcherUrl dispatcherUrl) {
        final List<ProcessorTask> list = getForReporting(dispatcherUrl);
        if (list.isEmpty()) {
            return null;
        }
        log.info("Number of tasks for reporting: " + list.size());
        final ProcessorCommParamsYaml.ReportTaskProcessingResult processingResult = new ProcessorCommParamsYaml.ReportTaskProcessingResult();
        for (ProcessorTask task : list) {
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
            setReportedOn(processorCode, dispatcherUrl, task.taskId);
        }
        return processingResult;
    }

    public void markAsFinishedWithError(String processorCode, DispatcherUrl dispatcherUrl, long taskId, String es) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            markAsFinished(processorCode, dispatcherUrl, taskId,
                    new FunctionApiData.FunctionExec(
                            null, null, null,
                            new FunctionApiData.SystemExecResult("system-error", false, -991, es)));
        }
    }

    void markAsFinished(String processorCode, DispatcherUrl dispatcherUrl, Long taskId, FunctionApiData.FunctionExec functionExec) {

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("markAsFinished({}, {}, {})", dispatcherUrl.url, taskId, functionExec);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.110 ProcessorTask wasn't found for Id #" + taskId);
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

                save(processorCode, task);
            }
        }
    }

    void markAsAssetPrepared(String processorCode, DispatcherUrl dispatcherUrl, Long taskId, boolean status) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("markAsAssetPrepared(dispatcherUrl: {}, taskId: {}, status: {})", dispatcherUrl, taskId, status);
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                log.error("#713.130 ProcessorTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(status);
                save(processorCode, task);
            }
        }
    }

    boolean isNeedNewTask(String processorCode) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            for (DispatcherUrl dispatcherUrl : map.keySet()) {

                // TODO 2019-10-24 need to optimize
                List<ProcessorTask> tasks = findAllByCompletedIsFalse(processorCode, dispatcherUrl);
                for (ProcessorTask task : tasks) {
                    // we don't need new task because execContext for this task is active
                    // i.e. there is a non-completed task with active execContext
                    // if execContext wasn't active we would need a new task
                    if (currentExecState.isStarted(new DispatcherUrl(task.dispatcherUrl), task.execContextId)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public List<ProcessorTask> findAllByCompletedIsFalse(String processorCode, DispatcherUrl dispatcherUrl) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            List<ProcessorTask> list = new ArrayList<>();
            for (ProcessorTask task : getMapForDispatcherUrl(dispatcherUrl).values()) {
                if (!task.completed) {
                    if (task.finishedOn!=null && task.reported && task.delivered && task.output.outputStatuses.stream().allMatch(o->o.uploaded)) {
                        task.completed = true;
                        save(processorCode, task);
                    }
                    list.add(task);
                }
            }
            return list;
        }
    }

    private Map<Long, ProcessorTask> getMapForDispatcherUrl(String processorCode, DispatcherUrl dispatcherUrl) {
        return map.computeIfAbsent(dispatcherUrl, m -> new HashMap<>());
    }

    public List<ProcessorTask> findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(String processorCode, boolean assetsPreparedStatus) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            List<ProcessorTask> list = new ArrayList<>();
            for (DispatcherUrl dispatcherUrl : map.keySet()) {
                Map<Long, ProcessorTask> mapForDispatcherUrl = getMapForDispatcherUrl(processorCode, dispatcherUrl);
                List<Long> forDeletion = new ArrayList<>();
                for (ProcessorTask task : mapForDispatcherUrl.values()) {
                    if (S.b(task.dispatcherUrl)) {
                        forDeletion.add(task.taskId);
                    }
                    if (!task.completed && task.finishedOn == null && task.assetsPrepared==assetsPreparedStatus) {
                        list.add(task);
                    }
                }
                forDeletion.forEach(id-> {
                    log.warn("#713.147 task #{} from dispatcher {} was deleted from global map with tasks", id, dispatcherUrl);
                    mapForDispatcherUrl.remove(id);
                });
            }
            return list;
        }
    }

    private Stream<ProcessorTask> findAllByFinishedOnIsNotNull(DispatcherUrl dispatcherUrl) {
        return getMapForDispatcherUrl(dispatcherUrl).values().stream().filter(o -> o.finishedOn!=null);
    }

    public void createTask(String processorCode, DispatcherUrl dispatcherUrl, long taskId, Long execContextId, String params) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            log.info("#713.150 Prepare new task #{}", taskId);
            Map<Long, ProcessorTask> mapForDispatcherUrl = getMapForDispatcherUrl(dispatcherUrl);
            ProcessorTask task = mapForDispatcherUrl.computeIfAbsent(taskId, k -> new ProcessorTask());

            task.taskId = taskId;
            task.execContextId = execContextId;
            task.params = params;
            task.functionExecResult = null;
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(params);
            task.clean = taskParamYaml.task.clean;
            task.dispatcherUrl = dispatcherUrl.url;
            task.createdOn = System.currentTimeMillis();
            task.assetsPrepared = false;
            task.launchedOn = null;
            task.finishedOn = null;
            task.reportedOn = null;
            task.reported = false;
            task.delivered = false;
            task.completed = false;
            taskParamYaml.task.outputs.stream()
                    .map(o->new ProcessorTask.OutputStatus(o.id, false) )
                    .collect(Collectors.toCollection(()->task.output.outputStatuses));

            File processorDir = new File(globals.processorDir, processorCode);
            if (!processorDir.exists()) {
                processorDir.mkdirs();
            }
            File processorTaskDir = new File(processorDir, Consts.TASK_DIR);

            File dispatcherDir = new File(processorTaskDir, metadataService.processorStateBydispatcherUrl(processorCode, dispatcherUrl).dispatcherCode);
            String path = getTaskPath(taskId);
            File taskDir = new File(dispatcherDir, path);
            try {
                //noinspection StatementWithEmptyBody
                if (taskDir.exists()) {
//                deleteOrRenameTaskDir(taskDir, taskYamlFile);
//                    FileUtils.deleteDirectory(taskDir);
                }
                else {
                    taskDir.mkdirs();
                }
                //noinspection ResultOfMethodCallIgnored
                taskDir.mkdirs();
                File taskYamlFile = new File(taskDir, Consts.TASK_YAML);
//                deleteYamlTaskFile(taskYamlFile);
                FileUtils.write(taskYamlFile, ProcessorTaskUtils.toString(task), Charsets.UTF_8, false);
            } catch (Throwable th) {
                String es = "#713.160 Error";
                log.error(es, th);
                throw new RuntimeException(es, th);
            }
        }
    }

    @Nullable
    public ProcessorTask resetTask(String processorCode, DispatcherUrl dispatcherUrl, Long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(null);
            return save(processorCode, task);
        }
    }

    @Nullable
    public ProcessorTask setLaunchOn(String processorCode, DispatcherUrl dispatcherUrl, long taskId) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            ProcessorTask task = findById(processorCode, dispatcherUrl, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(processorCode, task);
        }
    }

    private ProcessorTask save(String processorCode, ProcessorTask task) {
        File taskDir = prepareTaskDir(processorCode, new DispatcherUrl(task.dispatcherUrl), task.taskId);
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
            FileUtils.write(taskYaml, ProcessorTaskUtils.toString(task), Charsets.UTF_8, false);
        } catch (IOException e) {
            String es = "#713.200 Error while writing to file: " + taskYaml.getPath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return task;
    }

    @Nullable
    public ProcessorTask findById(String processorCode, DispatcherUrl dispatcherUrl, Long taskId) {
        if(true) throw new NotImplementedException("add processorCode");

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            return getMapForDispatcherUrl(dispatcherUrl)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().taskId.equals(taskId))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(null);
        }
    }

    public List<ProcessorTask> findAll(String processorCode, DispatcherUrl dispatcherUrl) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            Collection<ProcessorTask> values = getMapForDispatcherUrl(processorCode, dispatcherUrl).values();
            return List.copyOf(values);
        }
    }

    public List<ProcessorTask> findAll(String processorCode) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            List<ProcessorTask> list = new ArrayList<>();
            for (DispatcherUrl dispatcherUrl : map.keySet()) {
                list.addAll( getMapForDispatcherUrl(processorCode, dispatcherUrl).values());
            }
            return list;
        }
    }

    public void delete(String processorCode, DispatcherUrl dispatcherUrl, final long taskId) {
        MetadataParamsYaml.ProcessorState processorState = metadataService.processorStateBydispatcherUrl(processorCode, dispatcherUrl);

        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            final File processorDir = new File(globals.processorDir, processorCode);
            final File processorTaskDir = new File(processorDir, Consts.TASK_DIR);
            final File dispatcherDir = new File(processorTaskDir, processorState.dispatcherCode);

            final String path = getTaskPath(taskId);
            final File taskDir = new File(dispatcherDir, path);
            try {
                if (taskDir.exists()) {
                    deleteDir(taskDir, "delete dir in ProcessorTaskService.delete()");
                }
                Map<Long, ProcessorTask> mapTask = getMapForDispatcherUrl(dispatcherUrl);
                log.debug("Does task present in map before deleting: {}", mapTask.containsKey(taskId));
                mapTask.remove(taskId);
                log.debug("Does task present in map after deleting: {}", mapTask.containsKey(taskId));
            } catch (Throwable th) {
                log.error("#713.210 Error deleting task " + taskId, th);
            }
        }
    }

    private String getTaskPath(long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        return ""+power.power7+File.separatorChar+power.power4+File.separatorChar;
    }

    File prepareTaskDir(String processorCode, DispatcherUrl dispatcherUrl, Long taskId) {
        MetadataParamsYaml.ProcessorState processorState = metadataService.processorStateBydispatcherUrl(processorCode, dispatcherUrl);
        return prepareTaskDir(processorCode, processorState, taskId);
    }

    File prepareTaskDir(String processorCode, MetadataParamsYaml.ProcessorState processorState, Long taskId) {
        final File processorDir = new File(globals.processorDir, processorCode);
        final File processorTaskDir = new File(processorDir, Consts.TASK_DIR);
        final File dispatcherDir = new File(processorTaskDir, processorState.dispatcherCode);
        File taskDir = new File(dispatcherDir, getTaskPath(taskId));
        if (taskDir.exists()) {
            return taskDir;
        }
        //noinspection unused
        boolean status = taskDir.mkdirs();
        return taskDir;
    }

    @Nullable
    File prepareTaskSubDir(File taskDir, String subDir) {
        File taskSubDir = new File(taskDir, subDir);
        //noinspection ResultOfMethodCallIgnored
        taskSubDir.mkdirs();
        if (!taskSubDir.exists()) {
            log.warn("#713.220 Can't create taskSubDir: {}", taskSubDir.getAbsolutePath());
            return null;
        }
        return taskSubDir;
    }

    @Data
    @AllArgsConstructor
    public static class EnvYamlShort {
        public final Map<String, String> envs;
        public final List<EnvParamsYaml.DiskStorage> disk;

        public EnvYamlShort(EnvParamsYaml envYaml) {
            this.envs = envYaml.envs;
            this.disk = envYaml.disk;
        }
    }

    private static Yaml getYamlForEnvYamlShort() {
        return YamlUtils.init(EnvYamlShort.class);
    }

    private static String envYamlShortToString(EnvYamlShort envYamlShort) {
        return YamlUtils.toString(envYamlShort, getYamlForEnvYamlShort());
    }

    @Nullable
    public String prepareEnvironment(File artifactDir) {
        File envFile = new File(artifactDir, ConstsApi.MH_ENV_FILE);
        if (envFile.isDirectory()) {
            return "#713.220 path "+ artifactDir.getAbsolutePath()+" is dir, can't continue processing";
        }
        EnvYamlShort envYaml = new EnvYamlShort(envService.getEnvYaml());
        final String newEnv = envYamlShortToString(envYaml);

        try {
            FileUtils.writeStringToFile(envFile, newEnv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "#713.223 error creating "+ConstsApi.MH_ENV_FILE+", error: " + e.getMessage();
        }

        return null;
    }
}
