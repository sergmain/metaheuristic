/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.ai.utils.DigitUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.ai.yaml.metrics.Metrics;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.task.SimpleSnippet;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.station.StationTaskUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
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

@SuppressWarnings({"UnnecessaryLocalVariable", "WeakerAccess"})
@Service
@Slf4j
public class StationTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final TaskParamYamlUtils taskParamYamlUtils;

    private final Map<Long, StationTask> map = new ConcurrentHashMap<>();

    public StationTaskService(Globals globals, CurrentExecState currentExecState, TaskParamYamlUtils taskParamYamlUtils) {
        this.currentExecState = currentExecState;
        this.globals = globals;
        this.taskParamYamlUtils = taskParamYamlUtils;
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
            Files.list(globals.stationTaskDir.toPath()).forEach(p -> {
                final File topDir = p.toFile();
                if (!topDir.isDirectory()) {
                    return;
                }
                try {
                    Files.list(topDir.toPath()).forEach(s -> {
                        long taskId = Long.parseLong(topDir.getName()+s.toFile().getName());
                        File taskYamlFile = new File(s.toFile(), Consts.TASK_YAML);
                        if (taskYamlFile.exists()) {
                            try(FileInputStream fis = new FileInputStream(taskYamlFile)) {
                                StationTask task = StationTaskUtils.to(fis);
                                map.put(taskId, task);
                            }
                            catch (IOException e) {
                                log.error("Error #3", e);
                                throw new RuntimeException("Error #3", e);
                            }
                        }
                    });
                }
                catch (IOException e) {
                    log.error("Error #2", e);
                    throw new RuntimeException("Error #2", e);
                }
            });
        }
        catch (IOException e) {
            log.error("Error #1", e);
            throw new RuntimeException("Error #1", e);
        }
        //noinspection unused
        int i=0;
    }

    public void setReportedOn(long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setReportedOn({})", taskId);
            StationTask task = findById(taskId);
            if (task == null) {
                log.error("StationRestTask wasn't found for Id " + taskId);
                return;
            }
            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(task);
        }
    }

    public void setDelivered(Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setDelivered({})", taskId);
            StationTask task = findById(taskId);
            if (task == null) {
                log.error("StationTask wasn't found for Id {}", taskId);
                return;
            }
            task.setDelivered(true);
            save(task);
        }
    }

    public void setResourceUploaded(Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setResourceUploaded({})", taskId);
            StationTask task = findById(taskId);
            if (task == null) {
                log.error("StationTask wasn't found for Id {}", taskId);
                return;
            }
            task.setResourceUploaded(true);
            save(task);
        }
    }

    public List<StationTask> getForReporting() {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = findAllByFinishedOnIsNotNull();
            List<StationTask> result = list
                    .stream()
                    .filter(stationTask -> !stationTask.isReported() ||
                            (!stationTask.isDelivered() &&
                                    (stationTask.getReportedOn() == null || (System.currentTimeMillis() - stationTask.getReportedOn()) > 60_000)))
                    .collect(Collectors.toList());
            return result;
        }
    }

    void markAsFinishedIfAllOk(Long taskId, ExecProcessService.Result result) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsFinished({})", taskId);
            StationTask task = findById(taskId);
            if (task == null) {
                log.error("StationRestTask wasn't found for Id " + taskId);
            } else {
                task.setFinishedOn(System.currentTimeMillis());
                task.setDelivered(false);
                task.setReported(false);

                SnippetExec snippetExec = new SnippetExec();
                snippetExec.setExec(result);

                task.setSnippetExecResult(SnippetExecUtils.toString(snippetExec));
                save(task);
            }
        }
    }

    void markAsAssetPrepared(Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsAssetPrepared({})", taskId);
            StationTask task = findById(taskId);
            if (task == null) {
                log.error("StationTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(true);
                save(task);
            }
        }
    }

    public void finishAndWriteToLog(long taskId, String es) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = findById(taskId);
            if (task == null) {
                log.error("StationTask wasn't found for Id {}", taskId);
                return;
            }
            log.warn(es);
            task.setLaunchedOn(System.currentTimeMillis());
            task.setFinishedOn(System.currentTimeMillis());

            SnippetExec snippetExec = new SnippetExec();
            snippetExec.setExec( new ExecProcessService.Result(false, -1, es) );
            task.setSnippetExecResult(SnippetExecUtils.toString(snippetExec));
            save(task);
        }
    }

    boolean isNeedNewTask(String stationId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            if (stationId == null) {
                return false;
            }
            List<StationTask> tasks = findAllByFinishedOnIsNull();
            for (StationTask task : tasks) {
                if (currentExecState.isStarted(task.flowInstanceId)) {
                    return false;
                }
            }
            return true;
        }
    }

    void storeExecResult(Long taskId, long startedOn, SimpleSnippet snippet, ExecProcessService.Result result, File artifactDir) {
        log.info("storeExecResult(taskId: {}, snippet code: {})", taskId, snippet.code);
        StationTask taskTemp = findById(taskId);
        if (taskTemp == null) {
            log.error("StationRestTask wasn't found for Id " + taskId);
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
                        log.error("Error reading metrics file {}", metricsFile.getAbsolutePath());
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

    public List<StationTask> findAllByFinishedOnIsNull() {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (StationTask task : map.values()) {
                if (task.finishedOn == null) {
                    list.add(task);
                }
            }
            return list;
        }
    }

    public List<StationTask> findAllByFinishedOnIsNullAndAssetsPreparedIs(boolean status) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (StationTask task : map.values()) {
                if (task.finishedOn == null && task.assetsPrepared==status) {
                    list.add(task);
                }
            }
            return list;
        }
    }

    private List<StationTask> findAllByFinishedOnIsNotNull() {
        List<StationTask> list = new ArrayList<>();
        for (StationTask task : map.values()) {
            if (task.finishedOn != null) {
                list.add(task);
            }
        }
        return list;
    }

    public Protocol.StationTaskStatus produceStationTaskStatus() {
        Protocol.StationTaskStatus status = new Protocol.StationTaskStatus(new ArrayList<>());
        List<StationTask> list = findAllByFinishedOnIsNull();
        for (StationTask task : list) {
            status.getStatuses().add( new Protocol.StationTaskStatus.SimpleStatus(task.getTaskId()));
        }
        return status;
    }

    public void createTask(String launchpadUrl, long taskId, Long flowInstanceId, String params) {
        if (launchpadUrl==null) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = map.computeIfAbsent(taskId, k -> new StationTask());

            task.taskId = taskId;
            task.flowInstanceId = flowInstanceId;
            task.createdOn = System.currentTimeMillis();
            task.params = params;
            task.finishedOn = null;
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(params);
            task.clean = taskParamYaml.clean;
            task.launchpadUrl = launchpadUrl;

            String path = getTaskPath(taskId);
            File systemDir = new File(globals.stationTaskDir, path);
            try {
                if (systemDir.exists()) {
                    FileUtils.deleteDirectory(systemDir);
                }
                //noinspection ResultOfMethodCallIgnored
                systemDir.mkdirs();
                File taskYamlFile = new File(systemDir, Consts.TASK_YAML);
                FileUtils.write(taskYamlFile, StationTaskUtils.toString(task), Charsets.UTF_8, false);
                map.put(task.taskId, task);
            } catch (Throwable th) {
                log.error("Error ", th);
                throw new RuntimeException("Error", th);
            }
        }
    }

    public StationTask setLaunchOn(long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = findById(taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(task);
        }
    }

    private StationTask save(StationTask task) {
        File taskDir = prepareTaskDir(task.taskId);
        File taskYaml = new File(taskDir, Consts.TASK_YAML);


        if (taskYaml.exists()) {
            log.debug("{} file exists. Make backup", taskYaml.getPath());
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
            log.error("Error", e);
            throw new IllegalStateException("Error while writing to file: " + taskYaml.getPath(), e);
        }
        return task;
    }

    public StationTask findById(Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            for (StationTask task : map.values()) {
                if (task.taskId == taskId) {
                    return task;
                }
            }
            return null;
        }
    }

    public Collection<StationTask> findAll() {
        synchronized (StationSyncHolder.stationGlobalSync) {
            return Collections.unmodifiableCollection(map.values());
        }
    }

    public void delete(final long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            final String path = getTaskPath(taskId);

            final File systemDir = new File(globals.stationTaskDir, path);
            try {
                if (systemDir.exists()) {
                    FileUtils.deleteDirectory(systemDir);
                    // IDK is that bug or side-effect. so delete one more time
                    FileUtils.deleteDirectory(systemDir);
                    map.remove(taskId);
                }
            } catch (Throwable th) {
                log.error("Error deleting task " + taskId, th);
            }
        }
    }

    private String getTaskPath(long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        return ""+power.power7+File.separatorChar+power.power4+File.separatorChar;
    }

    File prepareTaskDir(Long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        File taskDir = new File(globals.stationTaskDir,
                ""+power.power7+File.separatorChar+power.power4+File.separatorChar);
//        if (taskDir.exists()) {
//            return taskDir;
//        }
        taskDir.mkdirs();
        return taskDir;
    }

    File prepareTaskSubDir(File taskDir, String subDir) {
        File taskSubDir = new File(taskDir, subDir);
        taskSubDir.mkdirs();
        if (!taskSubDir.exists()) {
            log.warn("Can't create taskSubDir: {}", taskSubDir.getAbsolutePath());
            return null;
        }
        return taskSubDir;
    }

}
