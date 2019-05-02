/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.ai.utils.DigitUtils;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.metrics.Metrics;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.ai.yaml.station_task.StationTask;
import aiai.ai.yaml.station_task.StationTaskUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"UnnecessaryLocalVariable", "WeakerAccess"})
@Service
@Slf4j
@Profile("station")
public class StationTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final MetadataService metadataService;

    private final Map<String, Map<Long, StationTask>> map = new ConcurrentHashMap<>();

    public StationTaskService(Globals globals, CurrentExecState currentExecState, MetadataService metadataService) {
        this.currentExecState = currentExecState;
        this.globals = globals;
        this.metadataService = metadataService;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.stationTaskDir.exists()) {
            return;
        }
        try {
            Files.list(globals.stationTaskDir.toPath()).forEach(top -> {
                try {
                    String launchpadUrl = metadataService.findHostByCode(top.toFile().getName());
                    Files.list(top).forEach(p -> {
                        final File launchpadDir = p.toFile();
                        if (!launchpadDir.isDirectory()) {
                            return;
                        }
                        try {
                            Files.list(launchpadDir.toPath()).forEach(s -> {
                                String launchpadDirName = launchpadDir.getName();
                                String name = s.toFile().getName();
                                long taskId = Long.parseLong(launchpadDirName) * DigitUtils.DIV + Long.parseLong(name);
                                log.info("Found dir of task with id: {}, {}, {}", taskId, launchpadDirName, name);
                                File taskYamlFile = new File(s.toFile(), Consts.TASK_YAML);
                                if (taskYamlFile.exists()) {
                                    try(FileInputStream fis = new FileInputStream(taskYamlFile)) {
                                        StationTask task = StationTaskUtils.to(fis);
                                        getMapForLaunchpadUrl(launchpadUrl).put(taskId, task);

                                        // fix state of task
                                        SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResult());
                                        SnippetExec preSnippetExec = SnippetExecUtils.to(task.getPreSnippetExecResult());
                                        SnippetExec postSnippetExec = SnippetExecUtils.to(task.getPostSnippetExecResult());
                                        if (snippetExec!=null && !snippetExec.exec.isOk) {
                                            markAsFinished(launchpadUrl, taskId, snippetExec.exec,
                                                    preSnippetExec!=null ? preSnippetExec.exec : null,
                                                    postSnippetExec!=null ? postSnippetExec.exec : null);
                                        }
                                    }
                                    catch (IOException e) {
                                        String es = "#713.01 Error";
                                        log.error(es, e);
                                        throw new RuntimeException(es, e);
                                    }
                                }
                                else {
                                    String path = s.toFile().getPath();
                                    log.info("Delete not valid dir of task {}", path);
                                    try {
                                        FileUtils.deleteDirectory(s.toFile());
                                        // IDK is that bug or side-effect. so delete one more time
                                        FileUtils.deleteDirectory(s.toFile());
                                    } catch (IOException e) {
                                        log.warn("#713.06 Error while deleting dir " + path, e);
                                    }
                                }
                            });
                        }
                        catch (IOException e) {
                            String es = "#713.09 Error";
                            log.error(es, e);
                            throw new RuntimeException(es, e);
                        }
                    });
                } catch (IOException e) {
                    String es = "#713.14 Error";
                    log.error(es, e);
                    throw new RuntimeException(es, e);
                }
            });
        }
        catch (IOException e) {
            String es = "#713.17 Error";
            log.error(es, e);
            throw new RuntimeException(es, e);
        }
        //noinspection unused
        int i=0;
    }

    public void setReportedOn(String launchpadUrl, long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setReportedOn({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.21 StationRestTask wasn't found for Id " + taskId);
                return;
            }
            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(task);
        }
    }

    public void setDelivered(String launchpadUrl, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setDelivered({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.25 StationTask wasn't found for Id {}", taskId);
                return;
            }
            task.setDelivered(true);

            // if snippet has finished with an error,
            // then we don't have to set isCompleted any more
            // because we've already marked this task as completed
            if (!task.isCompleted()) {
                task.setCompleted(task.isResourceUploaded());
            }
            save(task);
        }
    }

    public void setResourceUploadedAndCompleted(String launchpadUrl, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setResourceUploadedAndCompleted({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.29 StationTask wasn't found for Id {}", taskId);
                return;
            }
            task.setResourceUploaded(true);
            task.setCompleted( task.isDelivered() );
            save(task);
        }
    }

    @SuppressWarnings("unused")
    public void setCompleted(String launchpadUrl, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setCompleted({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.33 StationTask wasn't found for Id {}", taskId);
                return;
            }
            task.setCompleted(true);
            save(task);
        }
    }

    public List<StationTask> getForReporting(String launchpadUrl) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            Stream<StationTask> stream = findAllByFinishedOnIsNotNull(launchpadUrl);
            List<StationTask> result = stream
                    .filter(stationTask -> !stationTask.isReported() ||
                            (!stationTask.isDelivered() &&
                                    (stationTask.getReportedOn() == null || (System.currentTimeMillis() - stationTask.getReportedOn()) > 60_000)))
                    .collect(Collectors.toList());
            return result;
        }
    }

    public void markAsFinishedWithError(String launchpadUrl, long taskId, String es) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            markAsFinished(launchpadUrl, taskId, new ExecProcessService.Result(false, -1, es), null, null);
        }
    }

    void markAsFinished(String launchpadUrl, Long taskId,
                        ExecProcessService.Result result,
                        ExecProcessService.Result preResult,
                        ExecProcessService.Result postResult) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsFinished({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.38 StationTask wasn't found for Id " + taskId);
            } else {
                if (task.getLaunchedOn()==null) {
                    log.info("\t713.38.1 task #{} doesn't have the launchedOn as inited", taskId);
                    task.setLaunchedOn(System.currentTimeMillis());
                }
                if (!result.isOk) {
                    log.info("\t713.38.2 task #{} finished with an error, set completed to true", taskId);
                    // there are some problems with this task. mark it as completed
                    task.setCompleted(true);
                }
                task.setFinishedOn(System.currentTimeMillis());
                task.setDelivered(false);
                task.setReported(false);

                task.setSnippetExecResult(SnippetExecUtils.toString(new SnippetExec(result)));
                task.setPreSnippetExecResult(SnippetExecUtils.toString(new SnippetExec(preResult)));
                task.setPostSnippetExecResult(SnippetExecUtils.toString(new SnippetExec(postResult)));

                save(task);
            }
        }
    }

    void markAsAssetPrepared(String launchpadUrl, Long taskId, boolean status) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsAssetPrepared(launchpadUrl: {}, taskId: {}, status: {})", launchpadUrl, taskId, status);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.42 StationTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(status);
                save(task);
            }
        }
    }

    boolean isNeedNewTask(String launchpadUrl, String stationId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            if (stationId == null) {
                return false;
            }
            List<StationTask> tasks = findAllByCompletedIsFalse(launchpadUrl);
            for (StationTask task : tasks) {
                // we don't need new task because flowInstance for this task is active
                if (currentExecState.isStarted(task.launchpadUrl, task.flowInstanceId)) {
                    return false;
                }
            }
            return true;
        }
    }

    void storeExecResult(String launchpadUrl, Long taskId, long startedOn, SnippetConfig snippet, ExecProcessService.Result result, File artifactDir) {
        log.info("storeExecResult(launchpadUrl: {}, taskId: {}, snippet code: {})", launchpadUrl, taskId, snippet.getCode());
        StationTask taskTemp = findById(launchpadUrl, taskId);
        if (taskTemp == null) {
            log.error("#713.49 StationRestTask wasn't found for Id " + taskId);
        } else {
            // store metrics after predict only
            if (snippet.isMetrics()) {
                File metricsFile = new File(artifactDir, Consts.METRICS_FILE_NAME);
                Metrics metrics = new Metrics();
                if (metricsFile.exists()) {
                    try {
                        String execMetrics = FileUtils.readFileToString(metricsFile, StandardCharsets.UTF_8);
                        metrics.setStatus(Metrics.Status.Ok);
                        metrics.setMetrics(execMetrics);
                    }
                    catch (IOException e) {
                        log.error("#713.53 Error reading metrics file {}", metricsFile.getAbsolutePath());
                        taskTemp.setMetrics("system-error: " + e.toString());
                        metrics.setStatus(Metrics.Status.Error);
                        metrics.setError(e.toString());
                    }
                } else {
                    metrics.setStatus(Metrics.Status.NotFound);
                }
                taskTemp.setMetrics(MetricsUtils.toString(metrics));
            }
            SnippetExec snippetExec = SnippetExecUtils.to(taskTemp.getSnippetExecResult());
            if (snippetExec==null) {
                snippetExec = new SnippetExec();
            }
            snippetExec.setExec(result);
            String yaml = SnippetExecUtils.toString(snippetExec);
            taskTemp.setSnippetExecResult(yaml);
            taskTemp.setLaunchedOn(startedOn);
            save(taskTemp);
        }
    }

    public List<StationTask> findAllByCompletedIsFalse(String launchpadUrl) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (StationTask task : getMapForLaunchpadUrl(launchpadUrl).values()) {
                if (!task.completed) {
                    list.add(task);
                }
            }
            return list;
        }
    }

    private Map<Long, StationTask> getMapForLaunchpadUrl(String launchpadUrl) {
        return map.computeIfAbsent(launchpadUrl, m -> new HashMap<>());
    }

    public List<StationTask> findAllByFinishedOnIsNullAndAssetsPreparedIs(boolean status) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (String launchpadUrl : map.keySet()) {
                for (StationTask task : getMapForLaunchpadUrl(launchpadUrl).values()) {
                    if (task.finishedOn == null && task.assetsPrepared==status) {
                        list.add(task);
                    }
                }
            }
            return list;
        }
    }

    private Stream<StationTask> findAllByFinishedOnIsNotNull(String launchpadUrl) {
        return getMapForLaunchpadUrl(launchpadUrl).values().stream().filter( o -> o.finishedOn!=null);
    }

    public Protocol.StationTaskStatus produceStationTaskStatus(String launchpadUrl) {
        Protocol.StationTaskStatus status = new Protocol.StationTaskStatus(new ArrayList<>());
        List<StationTask> list = findAll(launchpadUrl);
        for (StationTask task : list) {
            status.getStatuses().add( new Protocol.StationTaskStatus.SimpleStatus(task.getTaskId()));
        }
        return status;
    }

    public void createTask(String launchpadUrl, long taskId, Long flowInstanceId, String params) {
        if (launchpadUrl==null) {
            throw new IllegalStateException("#713.55 launchpadUrl is null");
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("Assign new task #{}, params:\n{}", taskId, params );
            Map<Long, StationTask> mapForLaunchpadUrl = getMapForLaunchpadUrl(launchpadUrl);
            StationTask task = mapForLaunchpadUrl.computeIfAbsent(taskId, k -> new StationTask());

            task.taskId = taskId;
            task.flowInstanceId = flowInstanceId;
            task.params = params;
            task.metrics = null;
            task.snippetExecResult = null;
            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(params);
            task.clean = taskParamYaml.clean;
            task.launchpadUrl = launchpadUrl;
            task.createdOn = System.currentTimeMillis();
            task.assetsPrepared = false;
            task.launchedOn = null;
            task.finishedOn = null;
            task.reportedOn = null;
            task.reported = false;
            task.delivered = false;
            task.resourceUploaded = false;
            task.completed = false;

            File launchpadDir = new File(globals.stationTaskDir, metadataService.launchpadUrlAsCode(launchpadUrl).code);
            String path = getTaskPath(taskId);
            File systemDir = new File(launchpadDir, path);
            File taskYamlFile = new File(systemDir, Consts.TASK_YAML);
            try {
                deleteOrRenameTaskDir(systemDir, taskYamlFile);
                //noinspection ResultOfMethodCallIgnored
                systemDir.mkdirs();
                FileUtils.write(taskYamlFile, StationTaskUtils.toString(task), Charsets.UTF_8, false);
            } catch (Throwable th) {
                String es = "#713.60 Error";
                log.error(es, th);
                throw new RuntimeException(es, th);
            }
        }
    }

    public static boolean deleteOrRenameTaskDir(File systemDir, File taskYamlFile) throws IOException {
        if (systemDir.exists()) {
            log.warn("#713.57 task's directory is exist, {}", systemDir.getPath());
            if (taskYamlFile.exists()) {
                File temp = new File(taskYamlFile.getParentFile(), Consts.TASK_YAML+".temp" );
                FileUtils.moveFile(taskYamlFile, temp);
                if (taskYamlFile.exists()) {
                    log.error("#713.58 File task.yaml still exists");
                }
            }
            File tempDir = new File(systemDir.getParentFile(), systemDir.getName() + ".temp");
            //noinspection ResultOfMethodCallIgnored
            systemDir.renameTo(tempDir);
            FileUtils.deleteDirectory(tempDir);
            if (systemDir.exists()) {
                log.error("c#713.59 an't delete or more task's dir {}", systemDir.getPath());
                return false;
            }
        }
        return true;
    }

    public StationTask setLaunchOn(String launchpadUrl, long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(task);
        }
    }

    private StationTask save(StationTask task) {
        File taskDir = prepareTaskDir(task.launchpadUrl, task.taskId);
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
            FileUtils.write(taskYaml, StationTaskUtils.toString(task), Charsets.UTF_8, false);
        } catch (IOException e) {
            String es = "#713.63 Error while writing to file: " + taskYaml.getPath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return task;
    }

    public StationTask findById(String launchpadUrl, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            for (StationTask task : getMapForLaunchpadUrl(launchpadUrl).values()) {
                if (task.taskId == taskId) {
                    return task;
                }
            }
            return null;
        }
    }

    public List<StationTask> findAll(String launchpadUrl) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            Collection<StationTask> values = getMapForLaunchpadUrl(launchpadUrl).values();
            return List.copyOf(values);
        }
    }

    public void delete(String launchpadUrl, final long taskId) {
        Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(launchpadUrl);

        synchronized (StationSyncHolder.stationGlobalSync) {
            final String path = getTaskPath(taskId);

            final File launchpadDir = new File(globals.stationTaskDir, launchpadCode.code);
            final File systemDir = new File(launchpadDir, path);
            try {
                if (systemDir.exists()) {
                    FileUtils.deleteDirectory(systemDir);
                    // IDK is that a bug or a side-effect. so delete one more time
                    FileUtils.deleteDirectory(systemDir);
                }
                Map<Long, StationTask> mapTask = getMapForLaunchpadUrl(launchpadUrl);
                log.debug("Does task present in map before deleting: {}", mapTask.containsKey(taskId));
                mapTask.remove(taskId);
                log.debug("Does task present in map after deleting: {}", mapTask.containsKey(taskId));
            } catch (Throwable th) {
                log.error("#713.66 Error deleting task " + taskId, th);
            }
        }
    }

    private String getTaskPath(long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        return ""+power.power7+File.separatorChar+power.power4+File.separatorChar;
    }

    File prepareTaskDir(String launchpadUrl, Long taskId) {
        Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(launchpadUrl);
        return prepareTaskDir(launchpadCode, taskId);
    }

    File prepareTaskDir(Metadata.LaunchpadInfo launchpadCode, Long taskId) {
        final File launchpadDir = new File(globals.stationTaskDir, launchpadCode.code);
        File taskDir = new File(launchpadDir, getTaskPath(taskId));
        if (taskDir.exists()) {
            return taskDir;
        }
        //noinspection unused
        boolean status = taskDir.mkdirs();
        return taskDir;
    }

    public File prepareSnippetDir(Metadata.LaunchpadInfo launchpadCode) {
        final File launchpadDir = new File(globals.stationResourcesDir, launchpadCode.code);
        if (launchpadDir.exists()) {
            return launchpadDir;
        }
        //noinspection unused
        boolean status = launchpadDir.mkdirs();
        return launchpadDir;
    }

    File prepareTaskSubDir(File taskDir, String subDir) {
        File taskSubDir = new File(taskDir, subDir);
        taskSubDir.mkdirs();
        if (!taskSubDir.exists()) {
            log.warn("#713.69 Can't create taskSubDir: {}", taskSubDir.getAbsolutePath());
            return null;
        }
        return taskSubDir;
    }

}
