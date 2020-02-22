/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.station.env.EnvService;
import ai.metaheuristic.ai.utils.DigitUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.ai.yaml.station_task.StationTaskUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYaml;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.Metrics;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
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

@SuppressWarnings({"UnnecessaryLocalVariable", "WeakerAccess"})
@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class StationTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;
    private final MetadataService metadataService;
    private final EnvService envService;

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
                    String mh.dispatcher.Url = metadataService.findHostByCode(top.toFile().getName());
                    if (mh.dispatcher.Url==null) {
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
                                    StationTask task = StationTaskUtils.to(fis);
                                    if (S.b(task.mh.dispatcher.Url)) {
                                        deleteDir(currDir, "#713.005 Delete not valid dir of task " + s);
                                        log.warn("#713.007 task #{} from mh.dispatcher. {} was deleted from disk because mh.dispatcher.Url field was empty", taskId, mh.dispatcher.Url);
                                        return;
                                    }
                                    getMapForLaunchpadUrl(mh.dispatcher.Url).put(taskId, task);

                                    // fix state of task
                                    FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResult());
                                    if (functionExec !=null &&
                                            ((functionExec.generalExec!=null && !functionExec.exec.isOk ) ||
                                                    (functionExec.generalExec!=null && !functionExec.generalExec.isOk))) {
                                        markAsFinished(mh.dispatcher.Url, taskId, functionExec);
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

    public void setReportedOn(String mh.dispatcher.Url, long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setReportedOn({}, {})", mh.dispatcher.Url, taskId);
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                log.error("#713.070 StationRestTask wasn't found for Id " + taskId);
                return;
            }
            task.setReported(true);
            task.setReportedOn(System.currentTimeMillis());
            save(task);
        }
    }

    public void setDelivered(String mh.dispatcher.Url, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setDelivered({}, {})", mh.dispatcher.Url, taskId);
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                log.error("#713.080 StationTask wasn't found for Id {}", taskId);
                return;
            }
            if (task.delivered) {
                return;
            }

            task.setDelivered(true);
            // if function has finished with an error,
            // then we don't have to set isCompleted any more
            // because we've already marked this task as completed
            if (!task.isCompleted()) {
                task.setCompleted(task.isResourceUploaded());
            }
            save(task);
        }
    }

    public void setResourceUploadedAndCompleted(String mh.dispatcher.Url, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setResourceUploadedAndCompleted({}, {})", mh.dispatcher.Url, taskId);
            StationTask task = findById(mh.dispatcher.Url, taskId);
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
    public void setCompleted(String mh.dispatcher.Url, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("setCompleted({}, {})", mh.dispatcher.Url, taskId);
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                log.error("#713.100 StationTask wasn't found for Id {}", taskId);
                return;
            }
            task.setCompleted(true);
            save(task);
        }
    }

    public List<StationTask> getForReporting(String mh.dispatcher.Url) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            Stream<StationTask> stream = findAllByFinishedOnIsNotNull(mh.dispatcher.Url);
            List<StationTask> result = stream
                    .filter(stationTask -> !stationTask.isReported() ||
                            (!stationTask.isDelivered() &&
                                    (stationTask.getReportedOn() == null || (System.currentTimeMillis() - stationTask.getReportedOn()) > 60_000)))
                    .collect(Collectors.toList());
            return result;
        }
    }

    public StationCommParamsYaml.ReportTaskProcessingResult reportTaskProcessingResult(String mh.dispatcher.Url) {
        final List<StationTask> list = getForReporting(mh.dispatcher.Url);
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
            StationCommParamsYaml.ReportTaskProcessingResult.MachineLearningTaskResult ml = null;
            Meta predictedData = MetaUtils.getMeta(task.metas, Consts.META_PREDICTED_DATA);
            if (task.getMetrics()!=null || predictedData!=null) {
                ml = new StationCommParamsYaml.ReportTaskProcessingResult.MachineLearningTaskResult(
                        task.getMetrics(), predictedData.getValue(), EnumsApi.Fitting.of(MetaUtils.getValue(task.metas, Consts.META_FITTED)));
            }
            final StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result =
                    new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getTaskId(), task.getFunctionExecResult(), ml);
            processingResult.results.add(result);
            setReportedOn(mh.dispatcher.Url, task.taskId);
        }
        return processingResult;
    }

    public void markAsFinishedWithError(String mh.dispatcher.Url, long taskId, String es) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            markAsFinished(mh.dispatcher.Url, taskId,
                    new FunctionApiData.FunctionExec(
                            null, null, null,
                            new FunctionApiData.SystemExecResult("system-error", false, -991, es)));
        }
    }

    void markAsFinished(String mh.dispatcher.Url, Long taskId, FunctionApiData.FunctionExec functionExec) {

        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsFinished({}, {})", mh.dispatcher.Url, taskId);
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                log.error("#713.110 StationTask wasn't found for Id #" + taskId);
            } else {
                if (task.getLaunchedOn()==null) {
                    log.info("#713.113 task #{} doesn't have the launchedOn as inited", taskId);
                    task.setLaunchedOn(System.currentTimeMillis());
                }
                if (!functionExec.allFunctionsAreOk()) {
                    log.info("#713.115 task #{} was finished with an error, set completed to true", taskId);
                    // there are some problems with this task. mark it as completed
                    task.setCompleted(true);
                }
                task.setFinishedOn(System.currentTimeMillis());
                task.setDelivered(false);
                task.setReported(false);
                task.setFunctionExecResult(FunctionExecUtils.toString(functionExec));

                save(task);
            }
        }
    }

    void markAsAssetPrepared(String mh.dispatcher.Url, Long taskId, boolean status) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("markAsAssetPrepared(mh.dispatcher.Url: {}, taskId: {}, status: {})", mh.dispatcher.Url, taskId, status);
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                log.error("#713.130 StationTask wasn't found for Id {}", taskId);
            } else {
                task.setAssetsPrepared(status);
                save(task);
            }
        }
    }

    boolean isNeedNewTask(String mh.dispatcher.Url, String stationId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            if (stationId == null) {
                return false;
            }
            // TODO 2019-10-24 need to optimize
            List<StationTask> tasks = findAllByCompletedIsFalse(mh.dispatcher.Url);
            for (StationTask task : tasks) {
                // we don't need new task because execContext for this task is active
                // i.e. there is a non-completed task with active execContext
                // if execContext wasn't active we would need a new task
                if (currentExecState.isStarted(task.mh.dispatcher.Url, task.execContextId)) {
                    return false;
                }
            }
            return true;
        }
    }

    public void storePredictedData(String mh.dispatcher.Url, StationTask task, TaskParamsYaml.FunctionConfig functionConfig, File artifactDir) throws IOException {
        Meta m = MetaUtils.getMeta(functionConfig.metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
        if (MetaUtils.isTrue(m)) {
            log.info("storePredictedData(mh.dispatcher.Url: {}, taskId: {}, function code: {})", mh.dispatcher.Url, task.taskId, functionConfig.getCode());
            String data = getPredictedData(artifactDir);
            if (data!=null) {
                task.getMetas().add( new Meta(Consts.META_PREDICTED_DATA, data, null) );
                save(task);
            }
        }
    }

    public void storeFittingCheck(String mh.dispatcher.Url, StationTask task, TaskParamsYaml.FunctionConfig functionConfig, File artifactDir) throws IOException {
        if (functionConfig.type.equals(CommonConsts.CHECK_FITTING_TYPE)) {
           log.info("storeFittingCheck(mh.dispatcher.Url: {}, taskId: {}, function code: {})", mh.dispatcher.Url, task.taskId, functionConfig.getCode());
            FittingYaml fittingYaml = getFittingCheck(artifactDir);
            if (fittingYaml != null) {
                task.getMetas().add(new Meta(Consts.META_FITTED, fittingYaml.fitting.toString(), null));
                save(task);
            }
            else {
                log.error("#713.137 file with testing of fitting wasn't found, task #{}, artifact dir: {}", task.taskId, artifactDir.getAbsolutePath());
            }
        }
    }

    public void storeMetrics(String mh.dispatcher.Url, StationTask task, TaskParamsYaml.FunctionConfig functionConfig, File artifactDir) {
        // store metrics after predict only
        if (functionConfig.ml!=null && functionConfig.ml.metrics) {
            log.info("storeMetrics(mh.dispatcher.Url: {}, taskId: {}, function code: {})", mh.dispatcher.Url, task.taskId, functionConfig.getCode());
            Metrics metrics = new Metrics();
            File metricsFile = getMetricsFile(artifactDir);
            if (metricsFile!=null) {
                try {
                    String execMetrics = FileUtils.readFileToString(metricsFile, StandardCharsets.UTF_8);
                    metrics.setStatus(EnumsApi.MetricsStatus.Ok);
                    metrics.setMetrics(execMetrics);
                }
                catch (IOException e) {
                    log.error("#713.140 Error reading metrics file {}", metricsFile.getAbsolutePath());
                    metrics.setStatus(EnumsApi.MetricsStatus.Error);
                    metrics.setError(e.toString());
                }
            } else {
                metrics.setStatus(EnumsApi.MetricsStatus.NotFound);
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


    private String getPredictedData(File artifactDir) throws IOException {
        File file = new File(artifactDir, Consts.MH_PREDICTION_DATA_FILE_NAME);
        if (file.exists() && file.isFile()) {
            String data = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            return data;
        }
        return null;
    }

    private FittingYaml getFittingCheck(File artifactDir) throws IOException {
        File file = new File(artifactDir, Consts.MH_FITTING_FILE_NAME);
        if (file.exists() && file.isFile()) {
            String yaml = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            return FittingYamlUtils.BASE_YAML_UTILS.to(yaml);
        }
        return null;
    }

    public List<StationTask> findAllByCompletedIsFalse(String mh.dispatcher.Url) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (StationTask task : getMapForLaunchpadUrl(mh.dispatcher.Url).values()) {
                if (!task.completed) {
                    list.add(task);
                }
            }
            return list;
        }
    }

    private Map<Long, StationTask> getMapForLaunchpadUrl(String mh.dispatcher.Url) {
        return map.computeIfAbsent(mh.dispatcher.Url, m -> new HashMap<>());
    }

    public List<StationTask> findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(boolean assetsPreparedStatus) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (String mh.dispatcher.Url : map.keySet()) {
                Map<Long, StationTask> mapForLaunchpadUrl = getMapForLaunchpadUrl(mh.dispatcher.Url);
                List<Long> forDelition = new ArrayList<>();
                for (StationTask task : mapForLaunchpadUrl.values()) {
                    if (S.b(task.mh.dispatcher.Url)) {
                        forDelition.add(task.taskId);
                    }
                    if (!task.completed && task.finishedOn == null && task.assetsPrepared==assetsPreparedStatus) {
                        list.add(task);
                    }
                }
                forDelition.forEach(id-> {
                    log.warn("#713.147 task #{} from mh.dispatcher. {} was deleted from global map with tasks", id, mh.dispatcher.Url);
                    mapForLaunchpadUrl.remove(id);
                });
            }
            return list;
        }
    }

    private Stream<StationTask> findAllByFinishedOnIsNotNull(String mh.dispatcher.Url) {
        return getMapForLaunchpadUrl(mh.dispatcher.Url).values().stream().filter( o -> o.finishedOn!=null);
    }

    public StationCommParamsYaml.ReportStationTaskStatus produceStationTaskStatus(String mh.dispatcher.Url) {
        List<StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus> statuses = new ArrayList<>();
        List<StationTask> list = findAll(mh.dispatcher.Url);
        for (StationTask task : list) {
            statuses.add( new StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus(task.getTaskId()));
        }
        return new StationCommParamsYaml.ReportStationTaskStatus(statuses);
    }

    public void createTask(String mh.dispatcher.Url, long taskId, Long execContextId, String params) {
        if (mh.dispatcher.Url==null) {
            throw new IllegalStateException("#713.150 mh.dispatcher.Url is null");
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            log.info("Assign new task #{}, params:\n{}", taskId, params );
            Map<Long, StationTask> mapForLaunchpadUrl = getMapForLaunchpadUrl(mh.dispatcher.Url);
            StationTask task = mapForLaunchpadUrl.computeIfAbsent(taskId, k -> new StationTask());

            task.taskId = taskId;
            task.execContextId = execContextId;
            task.params = params;
            task.metrics = null;
            task.functionExecResult = null;
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(params);
            task.clean = taskParamYaml.taskYaml.clean;
            task.mh.dispatcher.Url = mh.dispatcher.Url;
            task.createdOn = System.currentTimeMillis();
            task.assetsPrepared = false;
            task.launchedOn = null;
            task.finishedOn = null;
            task.reportedOn = null;
            task.reported = false;
            task.delivered = false;
            task.resourceUploaded = false;
            task.completed = false;

            File mh.dispatcher.Dir = new File(globals.stationTaskDir, metadataService.mh.dispatcher.UrlAsCode(mh.dispatcher.Url).code);
            String path = getTaskPath(taskId);
            File taskDir = new File(mh.dispatcher.Dir, path);
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

    public StationTask resetTask(String mh.dispatcher.Url, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(null);
            return save(task);
        }
    }

    public StationTask setLaunchOn(String mh.dispatcher.Url, long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            StationTask task = findById(mh.dispatcher.Url, taskId);
            if (task == null) {
                return null;
            }
            task.setLaunchedOn(System.currentTimeMillis());
            return save(task);
        }
    }

    private StationTask save(StationTask task) {
        File taskDir = prepareTaskDir(task.mh.dispatcher.Url, task.taskId);
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

    public StationTask findById(String mh.dispatcher.Url, Long taskId) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            return getMapForLaunchpadUrl(mh.dispatcher.Url)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().taskId == taskId)
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(null);
        }
    }

    public List<StationTask> findAll(String mh.dispatcher.Url) {
        synchronized (StationSyncHolder.stationGlobalSync) {
            Collection<StationTask> values = getMapForLaunchpadUrl(mh.dispatcher.Url).values();
            return List.copyOf(values);
        }
    }

    public List<StationTask> findAll() {
        synchronized (StationSyncHolder.stationGlobalSync) {
            List<StationTask> list = new ArrayList<>();
            for (String mh.dispatcher.Url : map.keySet()) {
                list.addAll( getMapForLaunchpadUrl(mh.dispatcher.Url).values());
            }
            return list;
        }
    }

    public void delete(String mh.dispatcher.Url, final long taskId) {
        Metadata.DispatcherInfo mh.dispatcher.Code = metadataService.mh.dispatcher.UrlAsCode(mh.dispatcher.Url);

        synchronized (StationSyncHolder.stationGlobalSync) {
            final String path = getTaskPath(taskId);

            final File mh.dispatcher.Dir = new File(globals.stationTaskDir, mh.dispatcher.Code.code);
            final File taskDir = new File(mh.dispatcher.Dir, path);
            try {
                if (taskDir.exists()) {
                    deleteDir(taskDir, "delete dir in StationTaskService.delete()");
                }
                Map<Long, StationTask> mapTask = getMapForLaunchpadUrl(mh.dispatcher.Url);
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

    File prepareTaskDir(String mh.dispatcher.Url, Long taskId) {
        Metadata.DispatcherInfo mh.dispatcher.Code = metadataService.mh.dispatcher.UrlAsCode(mh.dispatcher.Url);
        return prepareTaskDir(mh.dispatcher.Code, taskId);
    }

    File prepareTaskDir(Metadata.DispatcherInfo mh.dispatcher.Code, Long taskId) {
        final File mh.dispatcher.Dir = new File(globals.stationTaskDir, mh.dispatcher.Code.code);
        File taskDir = new File(mh.dispatcher.Dir, getTaskPath(taskId));
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

    @Data
    @AllArgsConstructor
    public static class EnvYamlShort {
        public final Map<String, String> envs;
    }

    private static Yaml getYamlForEnvYamlShort() {
        return YamlUtils.init(EnvYamlShort.class);
    }

    private static String envYamlShortToString(EnvYamlShort envYamlShort) {
        return YamlUtils.toString(envYamlShort, getYamlForEnvYamlShort());
    }

    public String prepareEnvironment(File artifactDir) {
        File envFile = new File(artifactDir, ConstsApi.MH_ENV_FILE);
        if (envFile.isDirectory()) {
            return "#713.220 path "+ artifactDir.getAbsolutePath()+" is dir, can't continue processing";
        }
        EnvYamlShort envYaml = new EnvYamlShort(envService.getEnvYaml().envs);
        final String newEnv = envYamlShortToString(envYaml);

        try {
            FileUtils.writeStringToFile(envFile, newEnv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "#713.223 error creating "+ConstsApi.MH_ENV_FILE+", error: " + e.getMessage();
        }

        return null;
    }
}
