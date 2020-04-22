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

package ai.metaheuristic.ai.dispatcher.experiment_result;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentResult;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentTask;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentTaskRepository;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlWithCache;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultTaskParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.TaskMachineLearningYaml;
import ai.metaheuristic.commons.yaml.task_ml.TaskMachineLearningYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.ZIP_EXT;
import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.ExperimentFeature;

@SuppressWarnings("Duplicates")
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentResultTopLevelService {

    private static final String ZIP_DIR = "zip";
    private static final String TASKS_DIR = "tasks";
    private static final String EXPERIMENT_YAML_FILE = "experiment.yaml";
    private static final String TASK_YAML_FILE = "task-%s.yaml";

    private final ExperimentResultRepository experimentResultRepository;
    private final ExperimentTaskRepository experimentTaskRepository;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    private static class ParamFilter {
        String key;
        int idx;

        ParamFilter(String filter) {
            final int endIndex = filter.lastIndexOf('-');
            this.key = filter.substring( 0, endIndex);
            this.idx = Integer.parseInt(filter.substring( endIndex+1));
        }
        static ParamFilter of(String filter) {
            return new ParamFilter(filter);
        }
    }

    public OperationStatusRest uploadExperiment(MultipartFile file) {
        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.010 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.020 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.030 only '.zip' file is supported, filename: " + originFilename);
        }
        File resultDir = DirUtils.createTempDir("import-result-to-experiment-result-");
        if (resultDir==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.033 Error, can't create temporary dir");
        }

        try {
            File importFile = new File(resultDir, "import.zip");
            FileUtils.copyInputStreamToFile(file.getInputStream(), importFile);
            ZipUtils.unzipFolder(importFile, resultDir);

            File zipDir = new File(resultDir, ZIP_DIR);
            if (!zipDir.exists()){
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#422.035 Error, zip directory doesn't exist at path " + resultDir.getAbsolutePath());
            }

            File tasksDir = new File(zipDir, TASKS_DIR);
            if (!tasksDir.exists()){
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#422.038 Error, tasks directory doesn't exist at path " + zipDir.getAbsolutePath());
            }

            File experimentFile = new File(zipDir, EXPERIMENT_YAML_FILE);
            if (!experimentFile.exists()){
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#422.040 Error, experiment.yaml file doesn't exist at path "+ zipDir.getAbsolutePath());
            }

            String params = FileUtils.readFileToString(experimentFile, StandardCharsets.UTF_8);

            ExperimentResult experimentResult = new ExperimentResult();
            LocalDate date = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
            String dateAsStr = date.format(formatter);

            experimentResult.name = "experiment uploaded on " + dateAsStr;
            experimentResult.description = experimentResult.name;
            experimentResult.code = experimentResult.name;
            experimentResult.params = params;
            experimentResult = experimentResultRepository.save(experimentResult);

            ExperimentResultParamsYaml apy = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(params);
            int count = 0;
            for (Long taskId : apy.taskIds) {
                if (++count%100==0) {
                    log.info("#422.045 Current number of imported task: {} of total {}", count, apy.taskIds.size());
                }
                File taskFile = new File(tasksDir, S.f(TASK_YAML_FILE, taskId));

                ExperimentTask at = new ExperimentTask();
                at.experimentResultId = experimentResult.id;
                at.taskId = taskId;
                at.params = FileUtils.readFileToString(taskFile, StandardCharsets.UTF_8);
                experimentTaskRepository.save(at);
            }
        }
        catch (Exception e) {
            log.error("#422.040 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.050 can't load functions, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(resultDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ResponseEntity<AbstractResource> exportExperimentResultToFile(Long experimentResultId) {
        File resultDir = DirUtils.createTempDir("prepare-file-export-result-");
        File zipDir = new File(resultDir, ZIP_DIR);
        zipDir.mkdir();
        if (!zipDir.exists()) {
            log.error("#422.060 Error, zip dir wasn't created, path: {}", zipDir.getAbsolutePath());
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        File taskDir = new File(zipDir, TASKS_DIR);
        taskDir.mkdir();
        if (!taskDir.exists()) {
            log.error("#422.070 Error, task dir wasn't created, path: {}", taskDir.getAbsolutePath());
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        File zipFile = new File(resultDir, S.f("export-%s.zip", experimentResultId));
        if (zipFile.isDirectory()) {
            log.error("#422.080 Error, path for zip file is actually directory, path: {}", zipFile.getAbsolutePath());
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.NOT_FOUND);
        }
        File exportFile = new File(zipDir, EXPERIMENT_YAML_FILE);
        try {
            FileUtils.write(exportFile, experimentResult.params, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("#422.090 Error", e);
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Set<Long> experimentTaskIds = experimentTaskRepository.findIdsByExperimentResultId(experimentResultId);

        ExperimentResultParamsYaml apy = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.params);
        if (experimentTaskIds.size()!=apy.taskIds.size()) {
            log.warn("numbers of tasks in params of stored experiment and in db are different, " +
                    "experimentTaskIds.size: {}, apy.taskIds.size: {}", experimentTaskIds.size(), apy.taskIds.size());
        }

        int count = 0;
        for (Long experimentTaskId : experimentTaskIds) {
            if (++count%100==0) {
                log.info("#422.095 Current number of exported task: {} of total {}", count, experimentTaskIds.size());
            }
            ExperimentTask at = experimentTaskRepository.findById(experimentTaskId).orElse(null);
            if (at==null) {
                log.error("#422.100 ExperimentResultTask wasn't found for is #{}", experimentTaskId);
                continue;
            }
            File taskFile = new File(taskDir, S.f(TASK_YAML_FILE, at.taskId));
            try {
                FileUtils.writeStringToFile(taskFile, at.params, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("#422.110 Error writing task's params to file {}", taskFile.getAbsolutePath());
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        ZipUtils.createZip(zipDir, zipFile);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDispositionFormData("attachment", zipFile.getName());
        return new ResponseEntity<>(new FileSystemResource(zipFile.toPath()), RestUtils.getHeader(httpHeaders, zipFile.length()), HttpStatus.OK);
    }

    public ExperimentResultData.ExperimentDataOnly getExperimentDataOnly(Long experimentResultId) {

        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultData.ExperimentDataOnly("#422.120 experiment wasn't found in experimentResult, experimentResultId: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.params, experimentResultId));
        } catch (YAMLException e) {
            String es = "#422.130 Can't parse an experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentResultData.ExperimentDataOnly(es);
        }
        if (ypywc.experimentResult.experiment == null) {
            return new ExperimentResultData.ExperimentDataOnly("#422.140 experiment wasn't found, experimentId: " + experimentResultId);
        }
        if (ypywc.experimentResult.execContext == null) {
            return new ExperimentResultData.ExperimentDataOnly("#422.150 experiment has broken ref to execContext, experimentId: " + experimentResultId);
        }
        if (ypywc.experimentResult.execContext.execContextId ==null ) {
            return new ExperimentResultData.ExperimentDataOnly("#422.160 experiment wasn't startet yet, experimentId: " + experimentResultId);
        }

        ExperimentApiData.ExperimentData experiment = new ExperimentApiData.ExperimentData();
        experiment.id = ypywc.experimentResult.experiment.experimentId;
        experiment.execContextId = ypywc.experimentResult.execContext.execContextId;

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        experiment.code = epy.experimentYaml.code;
        experiment.name = epy.experimentYaml.name;
        experiment.description = epy.experimentYaml.description;
        experiment.isAllTaskProduced = epy.processing.isAllTaskProduced;
        experiment.isFeatureProduced = epy.processing.isFeatureProduced;
        experiment.createdOn = epy.createdOn;
        experiment.numberOfTask = epy.processing.numberOfTask;
        experiment.hyperParams.addAll(epy.experimentYaml.hyperParams);

        ExperimentResultData.ExperimentDataOnly result = new ExperimentResultData.ExperimentDataOnly();
        if (experiment.getExecContextId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }

        result.experiment = experiment;
        result.experimentResultId = experimentResult.id;
        return result;
    }

    public ExperimentResultData.ExperimentInfoExtended getExperimentInfoExtended(Long experimentResultId) {

        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultData.ExperimentInfoExtended("#422.170 experiment wasn't found in experimentResult, experimentResultId: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.params, experimentResultId));
        } catch (YAMLException e) {
            String es = "#422.180 Can't parse an experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentResultData.ExperimentInfoExtended(es);
        }
        if (ypywc.experimentResult.experiment == null) {
            return new ExperimentResultData.ExperimentInfoExtended("#422.190 experiment wasn't found, experimentId: " + experimentResultId);
        }
        if (ypywc.experimentResult.execContext == null) {
            return new ExperimentResultData.ExperimentInfoExtended("#422.200 experiment has broken ref to execContext, experimentId: " + experimentResultId);
        }
        if (ypywc.experimentResult.execContext.execContextId ==null ) {
            return new ExperimentResultData.ExperimentInfoExtended("#422.210 experiment wasn't startet yet, experimentId: " + experimentResultId);
        }

        ExperimentApiData.ExperimentData experiment = new ExperimentApiData.ExperimentData();
        experiment.id = ypywc.experimentResult.experiment.experimentId;
        experiment.execContextId = ypywc.experimentResult.execContext.execContextId;

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        experiment.code = epy.experimentYaml.code;
        experiment.name = epy.experimentYaml.name;
        experiment.description = epy.experimentYaml.description;
        experiment.isAllTaskProduced = epy.processing.isAllTaskProduced;
        experiment.isFeatureProduced = epy.processing.isFeatureProduced;
        experiment.createdOn = epy.createdOn;
        experiment.numberOfTask = epy.processing.numberOfTask;
        experiment.hyperParams.addAll(epy.experimentYaml.hyperParams);



        for (HyperParam hyperParams : ypywc.getExperimentParamsYaml().experimentYaml.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            InlineVariableUtils.NumberOfVariants variants = InlineVariableUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants(variants.status ? variants.count : 0);
        }

        ExperimentResultData.ExperimentInfoExtended result = new ExperimentResultData.ExperimentInfoExtended();
        if (experiment.getExecContextId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }
        result.experimentResult = experimentResult;

/*
        ExecContextImpl execContext = new ExecContextImpl();
        execContext.setParams(ypywc.experimentResult.execContext.execContextParams);
        execContext.id = ypywc.experimentResult.execContext.execContextId;
        execContext.state = ypywc.experimentResult.execContext.execState;
*/
        List<ExecContextData.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(execContext);

        ExperimentResultData.ExperimentInfo experimentInfoResult = new ExperimentResultData.ExperimentInfo();
        experimentInfoResult.features = ypywc.getExperimentParamsYaml().processing.features
                .stream()
                .map(e -> ExperimentService.asExperimentFeatureData(e, taskVertices, epy.processing.taskFeatures)).collect(Collectors.toList());

/*
        experimentInfoResult.execContext = execContext;
        experimentInfoResult.execContextState = EnumsApi.ExecContextState.toState(execContext.state);
*/

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public OperationStatusRest experimentResultDeleteCommit(Long id) {
        Long experimentResultId = experimentResultRepository.findIdById(id);
        if (experimentResultId == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.220 experiment wasn't found in ExperimentResult, id: " + id);
        }
        final AtomicBoolean isFound = new AtomicBoolean();
        do {
            isFound.set(false);
            experimentTaskRepository.findAllAsTaskSimple(PageRequest.of(0, 10), experimentResultId)
                    .forEach(experimentTaskId -> {
                        isFound.set(true);
                        experimentTaskRepository.deleteById(experimentTaskId);
                    });
        } while (isFound.get());
        experimentResultRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }


    public ExperimentResultData.PlotData getPlotData(Long experimentResultId, Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultData.PlotData("#422.230 experiment wasn't found in ExperimentResult, id: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.params, experimentResultId));
        } catch (YAMLException e) {
            String es = "#422.240 Can't parse an experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentResultData.PlotData(es);
        }
        ExperimentFeature feature = ypywc.getFeature(featureId);
        if (feature==null) {
            return ExperimentResultData.EMPTY_PLOT_DATA;
        }
        ExperimentResultData.PlotData data = findExperimentTaskForPlot(experimentResultId, ypywc, feature, params, paramsAxis);
        // TODO 2019-07-23 right now 2D lines plot isn't working. need to investigate
        //  so it'll be 3D with a fake zero data
        fixData(data);
        return data;
    }

    @SuppressWarnings("Duplicates")
    private void fixData(ExperimentResultData.PlotData data) {
        if (data.x.size()==1) {
            data.x.add("stub");
            BigDecimal[][] z = new BigDecimal[data.z.length][2];
            for (int i = 0; i < data.z.length; i++) {
                z[i][0] = data.z[i][0];
                z[i][1] = BigDecimal.ZERO;
            }
            data.z = z;
        }
        else if (data.y.size()==1) {
            data.y.add("stub");
            BigDecimal[][] z = new BigDecimal[2][data.z[0].length];
            for (int i = 0; i < data.z[0].length; i++) {
                z[0][i] = data.z[0][i];
                z[1][i] = BigDecimal.ZERO;
            }
            data.z = z;
        }
    }

    private ExperimentResultData.PlotData findExperimentTaskForPlot(
            Long experimentResultId, ExperimentResultParamsYamlWithCache apywc, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (apywc.experimentResult.experiment == null || apywc.getExperimentParamsYaml().processing.features == null ) {
            return ExperimentResultData.EMPTY_PLOT_DATA;
        } else {
            List<ExperimentResultTaskParamsYaml> selected = getTasksForFeatureIdAndParams(experimentResultId, apywc, feature, params);
            return collectDataForPlotting(apywc, selected, paramsAxis);
        }
    }

    private List<ExperimentResultTaskParamsYaml> getTasksForFeatureIdAndParams(Long experimentResultId, ExperimentResultParamsYamlWithCache estb1, ExperimentFeature feature, String[] params) {
        final Map<Long, Integer> taskToTaskType = estb1.getExperimentParamsYaml().processing.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(feature.getId()))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        final Set<Long> taskIds = taskToTaskType.keySet();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<ExperimentTask> experimentTasks = experimentTaskRepository.findTasksById(experimentResultId, taskIds);
        List<ExperimentResultTaskParamsYaml> selected = experimentTasks.stream()
                .map(o-> ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                .filter(atpy -> atpy.execState > 1)
                .collect(Collectors.toList());

        if (!isEmpty(params)) {
            selected = filterTasks(estb1.getExperimentParamsYaml(), params, selected);
        }
        return selected;
    }

    private ExperimentResultData.PlotData collectDataForPlotting(ExperimentResultParamsYamlWithCache estb, List<ExperimentResultTaskParamsYaml> selected, String[] paramsAxis) {
        final ExperimentResultData.PlotData data = new ExperimentResultData.PlotData();
        final List<String> paramCleared = new ArrayList<>();
        for (String param : paramsAxis) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            if (!paramCleared.contains(param)) {
                paramCleared.add(param);
            }
        }
        if (paramCleared.size()!=2) {
            throw new IllegalStateException("#422.250 Wrong number of params for axes. Expected: 2, actual: " + paramCleared.size());
        }
        Map<String, Map<String, Integer>> map = estb.getHyperParamsAsMap(false);
        data.x.addAll(map.get(paramCleared.get(0)).keySet());
        data.y.addAll(map.get(paramCleared.get(1)).keySet());

        Map<String, Integer> mapX = new HashMap<>();
        int idx=0;
        for (String x : data.x) {
            mapX.put(x, idx++);
        }
        Map<String, Integer> mapY = new HashMap<>();
        idx=0;
        for (String y : data.y) {
            mapY.put(y, idx++);
        }

        data.z = new BigDecimal[data.y.size()][data.x.size()];
        for (int i = 0; i < data.y.size(); i++) {
            for (int j = 0; j < data.x.size(); j++) {
                data.z[i][j] = BigDecimal.ZERO;
            }
        }

        String metricKey = null;
        for (ExperimentResultTaskParamsYaml task : selected) {

            if (S.b((task.metrics))) {
                continue;
            }
            TaskMachineLearningYaml tmly = TaskMachineLearningYamlUtils.BASE_YAML_UTILS.to(task.metrics);
            MetricValues metricValues = MetricsUtils.getValues( tmly.metrics );
            if (metricValues==null) {
                continue;
            }
            if (metricKey==null) {
                for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                    metricKey = entry.getKey();
                    break;
                }
            }

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.taskParams);
            int idxX = 0;
            int idxY = 0;
            if (taskParamYaml.task.inline!=null) {
                idxX = mapX.get(taskParamYaml.task.inline.get(ConstsApi.MH_HYPER_PARAMS).get(paramCleared.get(0)));
                idxY = mapY.get(taskParamYaml.task.inline.get(ConstsApi.MH_HYPER_PARAMS).get(paramCleared.get(1)));
            }
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }


    private List<ExperimentResultTaskParamsYaml> filterTasks(ExperimentParamsYaml epy, String[] params, List<ExperimentResultTaskParamsYaml> tasks) {
        final Set<String> paramSet = new HashSet<>();
        final Set<String> paramFilterKeys = new HashSet<>();
        for (String param : params) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            paramSet.add(param);
            paramFilterKeys.add(ParamFilter.of(param).key);
        }
        final Map<String, Map<String, Integer>> paramByIndex = ExperimentService.getHyperParamsAsMap(epy);

        List<ExperimentResultTaskParamsYaml> selected = new ArrayList<>();
        for (ExperimentResultTaskParamsYaml task : tasks) {
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.taskParams);
            if (taskParamYaml.task.inline==null) {
                continue;
            }
            boolean[] isOk = new boolean[taskParamYaml.task.inline.get(ConstsApi.MH_HYPER_PARAMS).size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.task.inline.get(ConstsApi.MH_HYPER_PARAMS).entrySet()) {
                try {
                    if (!paramFilterKeys.contains(entry.getKey())) {
                        isOk[idx] = true;
                        continue;
                    }
                    final Map<String, Integer> map = paramByIndex.getOrDefault(entry.getKey(), new HashMap<>());
                    if (map.isEmpty()) {
                        continue;
                    }
                    if (map.size()==1) {
                        isOk[idx] = true;
                        continue;
                    }

                    boolean isFilter = paramSet.contains(entry.getKey() + "-" + paramByIndex.get(entry.getKey()).get(entry.getKey() + "-" + entry.getValue()));
                    if (isFilter) {
                        isOk[idx] = true;
                    }
                }
                finally {
                    idx++;
                }
            }
            if (isInclude(isOk)) {
                selected.add(task);
            }
        }
        return selected;
    }

    private boolean isInclude(boolean[] isOk ) {
        for (boolean b : isOk) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty(String[] params) {
        for (String param : params) {
            if (StringUtils.isNotBlank(param)) {
                return false;
            }
        }
        return true;
    }

    public ExperimentResultData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(long experimentResultId, Long experimentId, Long featureId) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultData.ExperimentFeatureExtendedResult("#422.260 experiment wasn't found in experimentResult, id: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.params, experimentResultId));
        } catch (YAMLException e) {
            final String es = "#422.270 Can't extract experiment from experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentResultData.ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature experimentFeature = ypywc.getFeature(featureId);
        if (experimentFeature == null) {
            return new ExperimentResultData.ExperimentFeatureExtendedResult("#422.280 feature wasn't found, experimentFeatureId: " + featureId);
        }

        ExperimentResultData.ExperimentFeatureExtendedResult result = prepareExperimentFeatures(experimentResultId, ypywc, experimentFeature);
        return result;
    }

    // TODO 2019-09-11 need to add unit-test
    private ExperimentResultData.ExperimentFeatureExtendedResult prepareExperimentFeatures(
            Long experimentResultId, ExperimentResultParamsYamlWithCache ypywc, final ExperimentFeature experimentFeature) {

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        final Map<Long, Integer> taskToTaskType = epy.processing.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(experimentFeature.id))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        List<Long> taskWIthTypes = ypywc.experimentResult.taskIds.stream()
                .filter(taskToTaskType::containsKey)
                .sorted(Long::compareTo)
                .limit(Consts.PAGE_REQUEST_10_REC.getPageSize() + 1)
                .collect(Collectors.toList());

        Slice<ExperimentResultTaskParamsYaml> tasks = new SliceImpl<>(
                taskWIthTypes.subList(0, Math.min(taskWIthTypes.size(), Consts.PAGE_REQUEST_10_REC.getPageSize()))
                        .stream()
                        .map(id-> experimentTaskRepository.findByExperimentResultIdAndTaskId(experimentResultId, id))
                        .filter(Objects::nonNull)
                        .map( o-> ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                        .collect(Collectors.toList()),
                Consts.PAGE_REQUEST_10_REC,
                taskWIthTypes.size()>10
        );

        ExperimentResultData.HyperParamResult hyperParamResult = new ExperimentResultData.HyperParamResult();
        for (HyperParam hyperParam : epy.experimentYaml.getHyperParams()) {
            InlineVariableUtils.NumberOfVariants variants = InlineVariableUtils.getNumberOfVariants(hyperParam.getValues());
            ExperimentResultData.HyperParamList list = new ExperimentResultData.HyperParamList(hyperParam.getKey());
            for (String value : variants.values) {
                list.getList().add( new ExperimentResultData.HyperParamElement(value, false));
            }
            if (list.getList().isEmpty()) {
                list.getList().add( new ExperimentResultData.HyperParamElement("<Error value>", false));
            }
            hyperParamResult.getElements().add(list);
        }

        final ExperimentResultData.MetricsResult metricsResult = new ExperimentResultData.MetricsResult();
        final List<Map<String, BigDecimal>> values = new ArrayList<>();

        tasks.stream()
                .filter(o->taskToTaskType.containsKey(o.taskId) && o.execState > 1)
                .forEach( o-> {
                    if (S.b((o.metrics))) {
                        return;
                    }
                    TaskMachineLearningYaml tmly = TaskMachineLearningYamlUtils.BASE_YAML_UTILS.to(o.metrics);
                    MetricValues metricValues = MetricsUtils.getValues( tmly.metrics );
                    if (metricValues==null) {
                        return;
                    }
                    for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                        metricsResult.metricNames.add(entry.getKey());
                    }
                    values.add(metricValues.values);

                });

        List<ExperimentResultData.MetricElement> elements = new ArrayList<>();
        for (Map<String, BigDecimal> value : values) {
            ExperimentResultData.MetricElement element = new ExperimentResultData.MetricElement();
            for (String metricName : metricsResult.metricNames) {
                element.values.add(value.get(metricName));
            }
            elements.add(element);
        }
        elements.sort(ExperimentService::compareMetricElement);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

/*
        ExecContextImpl execContext = new ExecContextImpl();
        execContext.setParams( ypywc.experimentResult.execContext.execContextParams);
        execContext.id = ypywc.experimentResult.execContext.execContextId;
        execContext.state = ypywc.experimentResult.execContext.execState;
*/
        List<ExecContextData.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(execContext);

        ExperimentResultData.ExperimentFeatureExtendedResult result = new ExperimentResultData.ExperimentFeatureExtendedResult();
        result.metricsResult = metricsResult;
        result.hyperParamResult = hyperParamResult;
        result.tasks = tasks;
        result.experimentFeature = ExperimentService.asExperimentFeatureData(experimentFeature, taskVertices, epy.processing.taskFeatures);
        result.consoleResult = new ExperimentResultData.ConsoleResult();

        return result;
    }

    public ExperimentResultData.ConsoleResult getTasksConsolePart(Long experimentResultId, Long taskId) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultData.ConsoleResult("#422.300 experiment wasn't found in experimentResult, id: " + experimentResultId);
        }

        ExperimentTask task = experimentTaskRepository.findByExperimentResultIdAndTaskId(experimentResultId, taskId);
        if (task==null ) {
            return new ExperimentResultData.ConsoleResult("#422.310 Can't find a console output");
        }
        ExperimentResultTaskParamsYaml atpy = ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(atpy.functionExecResults);
        if (functionExec ==null ) {
            return new ExperimentResultData.ConsoleResult("#422.313 Can't find a console output");
        }
        return new ExperimentResultData.ConsoleResult(functionExec.exec.exitCode, functionExec.exec.isOk, functionExec.exec.console);
    }

    public ExperimentResultData.ExperimentFeatureExtendedResult getFeatureProgressPart(Long experimentResultId, Long featureId, String[] params, Pageable pageable) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultData.ExperimentFeatureExtendedResult("#422.320 experiment wasn't found in ExperimentResult, id: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(experimentResult.params, experimentResultId));
        } catch (YAMLException e) {
            final String es = "#422.330 Can't extract experiment from experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentResultData.ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature feature = ypywc.getFeature(featureId);

/*
        ExecContextImpl execContext = new ExecContextImpl();
        execContext.setParams(ypywc.experimentResult.execContext.execContextParams);
        execContext.id = ypywc.experimentResult.execContext.execContextId;
        execContext.state = ypywc.experimentResult.execContext.execState;
*/
        List<ExecContextData.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(execContext);

        ExperimentResultData.ExperimentFeatureExtendedResult result = new ExperimentResultData.ExperimentFeatureExtendedResult();
        result.tasks = feature==null ?  Page.empty() : findTasks(experimentResultId, ypywc, ControllerUtils.fixPageSize(10, pageable), feature, params);
        result.experimentFeature = ExperimentService.asExperimentFeatureData(feature, taskVertices, ypywc.getExperimentParamsYaml().processing.taskFeatures);
        result.consoleResult = new ExperimentResultData.ConsoleResult();
        return result;
    }

    private Slice<ExperimentResultTaskParamsYaml> findTasks(Long experimentResultId, ExperimentResultParamsYamlWithCache estb, Pageable pageable, ExperimentFeature feature, String[] params) {
        if (feature == null) {
            return Page.empty();
        }
        List<ExperimentResultTaskParamsYaml> selected = getTasksForFeatureIdAndParams(experimentResultId, estb, feature, params);
        List<ExperimentResultTaskParamsYaml> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));

        ExperimentParamsYaml epy = estb.getExperimentParamsYaml();

        for (ExperimentResultTaskParamsYaml atpy : subList) {
            atpy.typeAsString = epy.processing.taskFeatures.stream()
                    .filter(tf->tf.taskId.equals(atpy.taskId))
                    .map(tf->EnumsApi.ExperimentTaskType.from(tf.taskType))
                    .findFirst()
                    .orElse(EnumsApi.ExperimentTaskType.UNKNOWN)
                    .toString();
        }
        Slice<ExperimentResultTaskParamsYaml> slice = new PageImpl<>(subList, pageable, selected.size());
        return slice;
    }
}
