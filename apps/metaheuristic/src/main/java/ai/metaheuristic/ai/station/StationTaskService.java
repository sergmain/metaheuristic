/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.utils.DigitUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metrics.Metrics;
import ai.metaheuristic.ai.yaml.metrics.MetricsUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.ai.yaml.station_task.StationTaskUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.ml.overfitting.OverfittingYaml;
import ai.metaheuristic.commons.yaml.ml.overfitting.OverfittingYamlUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
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

@SuppressWarnings({"UnnecessaryLocalVariable", "WeakerAccess"})
@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class StationTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final MetadataService metadataService;

    private final Map<String, Map<Long, StationTask>> map = new ConcurrentHashMap<>();

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
                    if (launchpadUrl==null) {
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
                                String launchpadDirName = taskGroupDir.getName();
                                final File currDir = s.toFile();
                                String name = currDir.getName();
                                long taskId = Long.parseLong(launchpadDirName) * DigitUtils.DIV + Long.parseLong(name);
                                log.info("Found dir of task with id: {}, {}, {}", taskId, launchpadDirName, name);
                                File taskYamlFile = new File(currDir, Consts.TASK_YAML);
                                if (taskYamlFile.exists()) {
                                    try(FileInputStream fis = new FileInputStream(taskYamlFile)) {
                                        StationTask task = StationTaskUtils.to(fis);
                                        getMapForLaunchpadUrl(launchpadUrl).put(taskId, task);

                                        // fix state of task
                                        SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResult());
                                        if (snippetExec!=null &&
                                                ((snippetExec.generalExec!=null && !snippetExec.exec.isOk ) ||
                                                        (snippetExec.generalExec!=null && !snippetExec.generalExec.isOk))) {
                                            markAsFinished(launchpadUrl, taskId, snippetExec);
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
                                }
                                else {
                                    deleteDir(currDir, "Delete not valid dir of task " + s);
                                }
                            });
/*
                            // TODO 2019-10-29 because of unknown errors with deleted dirs, this will be disabled
                            if (isEmpty.get()) {
                                if (taskGroupDir.exists()) {
                                    log.info("Start deleting empty dir " + taskGroupDir.getPath());
                                    if (!taskGroupDir.delete()) {
                                        log.warn("Unable to delete directory {}", taskGroupDir.getPath());
                                    }
                                }
                            }
*/
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
            // IDK is that bug or side-effect. so delete one more time
            // TODO 2019-10-29 because of unknown errors with deleted dirs, this will be disabled
/*
            if (f.exists()) {
                FileUtils.deleteDirectory(f);
            }
*/
        } catch (IOException e) {
            log.warn("#713.060 Error while deleting dir {}, error: {}", f.getPath(), e.toString());
        }
    }

    public void setReportedOn(String launchpadUrl, long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setReportedOn({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.070 StationRestTask wasn't found for Id " + taskId);
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
                log.error("#713.080 StationTask wasn't found for Id {}", taskId);
                return;
            }
            if (task.delivered) {
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
                log.error("#713.090 StationTask wasn't found for Id {}", taskId);
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
                log.error("#713.100 StationTask wasn't found for Id {}", taskId);
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

    public StationCommParamsYaml.ReportTaskProcessingResult reportTaskProcessingResult(String launchpadUrl) {
        final List<StationTask> list = getForReporting(launchpadUrl);
        if (list.isEmpty()) {
            return null;
        }
        log.info("Number of tasks for reporting: " + list.size());
        final StationCommParamsYaml.ReportTaskProcessingResult processingResult = new StationCommParamsYaml.ReportTaskProcessingResult();
        for (StationTask task : list) {
            if (task.isDelivered() && !task.isReported() ) {
                log.warn("#775.140 This state need to be investigated: (task.isDelivered() && !task.isReported())==true");
            }
            // TODO 2019-07-12 do we need to check against task.isReported()? isn't task.isDelivered() just enough?
            if (task.isDelivered() && task.isReported() ) {
                continue;
            }
            final StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result =
                    new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getTaskId(), task.getSnippetExecResult(), task.getMetrics());
            processingResult.results.add(result);
            setReportedOn(launchpadUrl, task.taskId);
        }
        return processingResult;
    }

    public void markAsFinishedWithError(String launchpadUrl, long taskId, String es) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            markAsFinished(launchpadUrl, taskId,
                    new SnippetApiData.SnippetExec(
                            null, null, null,
                            new SnippetApiData.SnippetExecResult("system-error", false, -991, es)));
        }
    }

    void markAsFinished(String launchpadUrl, Long taskId, SnippetApiData.SnippetExec snippetExec ) {

        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsFinished({}, {})", launchpadUrl, taskId);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.110 StationTask wasn't found for Id #" + taskId);
            } else {
                if (task.getLaunchedOn()==null) {
                    log.info("#713.113 task #{} doesn't have the launchedOn as inited", taskId);
                    task.setLaunchedOn(System.currentTimeMillis());
                }
                if (!snippetExec.allSnippetsAreOk()) {
                    log.info("#713.115 task #{} was finished with an error, set completed to true", taskId);
                    // there are some problems with this task. mark it as completed
                    task.setCompleted(true);
                }
                task.setFinishedOn(System.currentTimeMillis());
                task.setDelivered(false);
                task.setReported(false);
                task.setSnippetExecResult(SnippetExecUtils.toString(snippetExec));

                save(task);
            }
        }
    }

    void markAsAssetPrepared(String launchpadUrl, Long taskId, boolean status) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsAssetPrepared(launchpadUrl: {}, taskId: {}, status: {})", launchpadUrl, taskId, status);
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                log.error("#713.130 StationTask wasn't found for Id {}", taskId);
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
            // TODO 2019-10-24 need to optimize
            List<StationTask> tasks = findAllByCompletedIsFalse(launchpadUrl);
            for (StationTask task : tasks) {
                // we don't need new task because workbook for this task is active
                // i.e. there is a non-completed task with active workbook
                // if workbook wasn't active we would need a new task
                if (currentExecState.isStarted(task.launchpadUrl, task.workbookId)) {
                    return false;
                }
            }
            return true;
        }
    }

    public void storeOverfitting(String launchpadUrl, StationTask task, SnippetConfigYaml snippet, File artifactDir) throws IOException {
        Meta m = MetaUtils.getMeta(snippet.metas, ConstsApi.META_MH_OVERFITTING_DETECTION_SUPPORTED);
        if (MetaUtils.isTrue(m)) {
            log.info("storeOverfitting(launchpadUrl: {}, taskId: {}, snippet code: {})", launchpadUrl, task.taskId, snippet.getCode());
            OverfittingYaml overfittingYaml = getOverfitting(artifactDir);
            if (overfittingYaml!=null) {
                task.getMetas().add( new Meta(Consts.META_OVERFITTED, Boolean.toString(overfittingYaml.overfitting), null) );
                save(task);
            }
        }
    }

    public void storeMetrics(String launchpadUrl, StationTask task, SnippetConfigYaml snippet, File artifactDir) {
        // store metrics after predict only
        if (snippet.ml!=null && snippet.ml.metrics) {
            log.info("storeMetrics(launchpadUrl: {}, taskId: {}, snippet code: {})", launchpadUrl, task.taskId, snippet.getCode());
            Metrics metrics = new Metrics();
            File metricsFile = getMetricsFile(artifactDir);
            if (metricsFile!=null) {
                try {
                    String execMetrics = FileUtils.readFileToString(metricsFile, StandardCharsets.UTF_8);
                    metrics.setStatus(Metrics.Status.Ok);
                    metrics.setMetrics(execMetrics);
                }
                catch (IOException e) {
                    log.error("#713.140 Error reading metrics file {}", metricsFile.getAbsolutePath());
                    metrics.setStatus(Metrics.Status.Error);
                    metrics.setError(e.toString());
                }
            } else {
                metrics.setStatus(Metrics.Status.NotFound);
            }
            task.setMetrics(MetricsUtils.toString(metrics));
            save(task);
        }
    }

    @SuppressWarnings("deprecation")
    private File getMetricsFile(File artifactDir) {
        File metricsFile = new File(artifactDir, Consts.MH_METRICS_FILE_NAME);
        if (metricsFile.exists()) {
            return metricsFile;
        }
        // let's try a file with legacy name
        metricsFile = new File(artifactDir, Consts.METRICS_FILE_NAME);
        return metricsFile.exists() ? metricsFile : null;
    }


    private OverfittingYaml getOverfitting(File artifactDir) throws IOException {
        File file = new File(artifactDir, Consts.MH_OVERFITTING_FILE_NAME);
        if (file.exists() && file.isFile()) {
            String yaml = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            return OverfittingYamlUtils.BASE_YAML_UTILS.to(yaml);
        }
        return null;
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

    public List<StationTask> findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(boolean assetsPreparedStatus) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (String launchpadUrl : map.keySet()) {
                for (StationTask task : getMapForLaunchpadUrl(launchpadUrl).values()) {
                    if (!task.completed && task.finishedOn == null && task.assetsPrepared==assetsPreparedStatus) {
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

    public StationCommParamsYaml.ReportStationTaskStatus produceStationTaskStatus(String launchpadUrl) {
        List<StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus> statuses = new ArrayList<>();
        List<StationTask> list = findAll(launchpadUrl);
        for (StationTask task : list) {
            statuses.add( new StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus(task.getTaskId()));
        }
        return new StationCommParamsYaml.ReportStationTaskStatus(statuses);
    }

    public void createTask(String launchpadUrl, long taskId, Long workbookId, String params) {
        if (launchpadUrl==null) {
            throw new IllegalStateException("#713.150 launchpadUrl is null");
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("Assign new task #{}, params:\n{}", taskId, params );
            Map<Long, StationTask> mapForLaunchpadUrl = getMapForLaunchpadUrl(launchpadUrl);
            StationTask task = mapForLaunchpadUrl.computeIfAbsent(taskId, k -> new StationTask());

            task.taskId = taskId;
            task.workbookId = workbookId;
            task.params = params;
            task.metrics = null;
            task.snippetExecResult = null;
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(params);
            task.clean = taskParamYaml.taskYaml.clean;
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
            File taskDir = new File(launchpadDir, path);
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
                FileUtils.write(taskYamlFile, StationTaskUtils.toString(task), Charsets.UTF_8, false);
            } catch (Throwable th) {
                String es = "#713.160 Error";
                log.error(es, th);
                throw new RuntimeException(es, th);
            }
        }
    }

    public StationTask resetTask(String launchpadUrl, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = findById(launchpadUrl, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(null);
            return save(task);
        }
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
            String es = "#713.200 Error while writing to file: " + taskYaml.getPath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return task;
    }

    public StationTask findById(String launchpadUrl, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            return getMapForLaunchpadUrl(launchpadUrl)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().taskId == taskId)
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(null);
        }
    }

    public List<StationTask> findAll(String launchpadUrl) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            Collection<StationTask> values = getMapForLaunchpadUrl(launchpadUrl).values();
            return List.copyOf(values);
        }
    }

    public List<StationTask> findAll() {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (String launchpadUrl : map.keySet()) {
                list.addAll( getMapForLaunchpadUrl(launchpadUrl).values());
            }
            return list;
        }
    }

    public void delete(String launchpadUrl, final long taskId) {
        Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(launchpadUrl);

        synchronized (StationSyncHolder.stationGlobalSync) {
            final String path = getTaskPath(taskId);

            final File launchpadDir = new File(globals.stationTaskDir, launchpadCode.code);
            final File taskDir = new File(launchpadDir, path);
            try {
                if (taskDir.exists()) {
                    deleteDir(taskDir, "delete dir in StationTaskService.delete()");
                }
                Map<Long, StationTask> mapTask = getMapForLaunchpadUrl(launchpadUrl);
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

}
