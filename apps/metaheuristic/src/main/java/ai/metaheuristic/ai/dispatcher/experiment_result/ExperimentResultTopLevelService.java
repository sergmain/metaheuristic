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

package ai.metaheuristic.ai.dispatcher.experiment_result;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentResult;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentTask;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentTaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsJsonUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlWithCache;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultTaskParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.json.versioning_json.JsonForVersioning;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
import static ai.metaheuristic.ai.Enums.FeatureExecStatus;
import static ai.metaheuristic.ai.dispatcher.data.ExperimentResultData.*;
import static ai.metaheuristic.api.data.experiment.ExperimentApiData.ExperimentFeatureData;
import static ai.metaheuristic.api.data.experiment.ExperimentApiData.HyperParam;
import static ai.metaheuristic.api.data.experiment_result.ExperimentResultApiData.ExperimentResultData;
import static ai.metaheuristic.api.data.experiment_result.ExperimentResultParams.ExperimentFeature;
import static ai.metaheuristic.api.data.experiment_result.ExperimentResultParams.ExperimentTaskFeature;

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

    public OperationStatusRest uploadExperiment(MultipartFile file, DispatcherContext context) {
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
        File resultDir = DirUtils.createMhTempDir("import-result-to-experiment-result-");
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
            ExperimentResultParams apy = ExperimentResultParamsJsonUtils.BASE_UTILS.to(params);

            ExperimentResult experimentResult = new ExperimentResult();
            LocalDate date = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String dateAsStr = date.format(formatter);

            experimentResult.name = apy.name;
            experimentResult.description = "experiment results "+apy.name+" uploaded on " + dateAsStr + " from file " + originFilename;
            experimentResult.code = apy.code;
            experimentResult.params = params;
            experimentResult.companyId = context.getCompanyId();
            experimentResult.createdOn = System.currentTimeMillis();
            experimentResult = experimentResultRepository.save(experimentResult);

            int count = 0;
            for (ExperimentTaskFeature taskFeature : apy.taskFeatures) {
                if (++count%100==0) {
                    log.info("#422.045 Current number of imported task: {} of total {}", count, apy.taskFeatures.size());
                }
                File taskFile = new File(tasksDir, S.f(TASK_YAML_FILE, taskFeature.taskId));

                ExperimentTask at = new ExperimentTask();
                at.experimentResultId = experimentResult.id;
                at.taskId = taskFeature.taskId;
                at.params = FileUtils.readFileToString(taskFile, StandardCharsets.UTF_8);
                experimentTaskRepository.save(at);
            }
        }
        catch (Exception e) {
            log.error("#422.040 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.050 can't load experiment results, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(resultDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public CleanerInfo exportExperimentResultToFile(Long experimentResultId) {
        CleanerInfo resource = new CleanerInfo();
        try {
            File resultDir = DirUtils.createMhTempDir("prepare-file-export-result-");
            resource.toClean.add(resultDir);

            File zipDir = new File(resultDir, ZIP_DIR);
            zipDir.mkdir();
            if (!zipDir.exists()) {
                log.error("#422.060 Error, zip dir wasn't created, path: {}", zipDir.getAbsolutePath());
                resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
                return resource;
            }
            File taskDir = new File(zipDir, TASKS_DIR);
            taskDir.mkdir();
            if (!taskDir.exists()) {
                log.error("#422.070 Error, task dir wasn't created, path: {}", taskDir.getAbsolutePath());
                resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
                return resource;
            }
            File zipFile = new File(resultDir, S.f("export-%s.zip", experimentResultId));
            if (zipFile.isDirectory()) {
                log.error("#422.080 Error, path for zip file is actually directory, path: {}", zipFile.getAbsolutePath());
                resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
                return resource;
            }
            ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
            if (experimentResult == null) {
                resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.NOT_FOUND);
                return resource;
            }
            File exportFile = new File(zipDir, EXPERIMENT_YAML_FILE);
            FileUtils.write(exportFile, experimentResult.params, StandardCharsets.UTF_8);
            Set<Long> experimentTaskIds = experimentTaskRepository.findIdsByExperimentResultId(experimentResultId);

            ExperimentResultParams apy = ExperimentResultParamsJsonUtils.BASE_UTILS.to(experimentResult.params);
            if (experimentTaskIds.size() != apy.taskFeatures.size()) {
                log.warn("numbers of tasks in params of stored experiment and in db are different, " +
                        "experimentTaskIds.size: {}, apy.taskIds.size: {}", experimentTaskIds.size(), apy.taskFeatures.size());
            }

            int count = 0;
            for (Long experimentTaskId : experimentTaskIds) {
                if (++count % 100 == 0) {
                    log.info("#422.095 Current number of exported task: {} of total {}", count, experimentTaskIds.size());
                }
                ExperimentTask at = experimentTaskRepository.findById(experimentTaskId).orElse(null);
                if (at == null) {
                    log.error("#422.100 ExperimentResultTask wasn't found for is #{}", experimentTaskId);
                    continue;
                }
                File taskFile = new File(taskDir, S.f(TASK_YAML_FILE, at.taskId));
                try {
                    FileUtils.writeStringToFile(taskFile, at.params, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.error("#422.110 Error writing task's params to file {}", taskFile.getAbsolutePath());
                    resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
                    return resource;
                }
            }

            ZipUtils.createZip(zipDir, zipFile);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            httpHeaders.setContentDisposition(ContentDisposition.parse(
                    "filename*=UTF-8''experiment-result-"+experimentResultId+".zip"));
            resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), RestUtils.getHeader(httpHeaders, zipFile.length()), HttpStatus.OK);
            return resource;
        }
        catch(Throwable th) {
            log.error("#422.115 General error", th);
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
            return resource;
        }
    }

    public ExperimentResultSimpleResult getExperimentResultData(Long experimentResultId) {

        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentResultSimpleResult("#422.120 experiment wasn't found in experimentResult, experimentResultId: " + experimentResultId);
        }

        ExperimentResultSimpleResult result = new ExperimentResultSimpleResult();
        result.experimentResult = new ExperimentResultSimple();
        result.experimentResult.code = experimentResult.code;
        result.experimentResult.name = experimentResult.name;
        result.experimentResult.description = experimentResult.description;
        result.experimentResult.createdOn = experimentResult.createdOn;
        result.experimentResult.id = experimentResultId;

        return result;
    }

    public ExperimentInfoExtended getExperimentInfoExtended(Long experimentResultId) {

        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentInfoExtended("#422.170 experiment wasn't found in experimentResult, experimentResultId: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            final ExperimentResultParams erpy = checkVersionAndUpgrade(experimentResult.id, experimentResult.params);
            if (erpy==null) {
                String es = "#422.177 Can't prepare experimentResult #" + experimentResult.id;
                log.error(es);
                return new ExperimentInfoExtended(es);
            }
            ypywc = new ExperimentResultParamsYamlWithCache(erpy);
        }
        catch (Throwable th) {
            String es = "#422.180 Can't parse an experimentResult, error: " + th.toString();
            log.error(es, th);
            return new ExperimentInfoExtended(es);
        }
        if (ypywc.experimentResult.execContext == null) {
            return new ExperimentInfoExtended("#422.200 experiment has broken ref to execContext, experimentId: " + experimentResultId);
        }
        if (ypywc.experimentResult.execContext.execContextId ==null ) {
            return new ExperimentInfoExtended("#422.210 experiment wasn't startet yet, experimentId: " + experimentResultId);
        }

        ExperimentResultData experiment = new ExperimentResultData();
        experiment.id = experimentResult.id;
        experiment.execContextId = ypywc.experimentResult.execContext.execContextId;
        experiment.code = ypywc.experimentResult.code;
        experiment.name = ypywc.experimentResult.name;
        experiment.description = ypywc.experimentResult.description;
        experiment.createdOn = ypywc.experimentResult.createdOn;
        experiment.numberOfTask = ypywc.experimentResult.numberOfTask;
        experiment.hyperParams.addAll(ypywc.experimentResult.hyperParams);


        for (HyperParam hyperParams : ypywc.experimentResult.hyperParams) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            InlineVariableUtils.NumberOfVariants variants = InlineVariableUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants(variants.status ? variants.count : 0);
        }

        ExperimentInfoExtended result = new ExperimentInfoExtended();
        if (experiment.getExecContextId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }
        result.experimentResult = experimentResult;

        ExecContextImpl execContext = new ExecContextImpl();
        execContext.setParams(ypywc.experimentResult.execContext.execContextParams);
        execContext.id = ypywc.experimentResult.execContext.execContextId;
        execContext.state = EnumsApi.ExecContextState.FINISHED.code;

        ExperimentInfo experimentInfoResult = new ExperimentInfo();
        experimentInfoResult.features = List.of();
        experimentInfoResult.features = ypywc.experimentResult.features
                .stream()
                .map(e -> asExperimentFeatureData(e, ypywc.experimentResult.taskFeatures)).collect(Collectors.toList());

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private ExperimentResultParams checkVersionAndUpgrade(Long experimentResultId, String params) {

        ParamsVersion v = JsonForVersioning.getParamsVersion(params);

        final String newParams;
        switch(v.getActualVersion()) {
            case 1:
            default:
                newParams = params;
                break;
        }

        final ExperimentResultParams experimentResult = ExperimentResultParamsJsonUtils.BASE_UTILS.to(newParams);
        return experimentResult;
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


    public PlotData getPlotData(Long experimentResultId, Long featureId, String[] params, String[] paramsAxis) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new PlotData("#422.230 experiment wasn't found in ExperimentResult, id: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsJsonUtils.BASE_UTILS.to(experimentResult.params));
        } catch (YAMLException e) {
            String es = "#422.240 Can't parse an experimentResult, error: " + e.toString();
            log.error(es, e);
            return new PlotData(es);
        }
        ExperimentFeature feature = ypywc.getFeature(featureId);
        if (feature==null) {
            return EMPTY_PLOT_DATA;
        }
        PlotData data = findExperimentTaskForPlot(experimentResultId, ypywc, feature, params, paramsAxis);
        // TODO 2019-07-23 right now 2D lines plot isn't working. need to investigate
        //  so it'll be 3D with a fake zero data
        fixData(data);
        return data;
    }

    public static ExperimentFeatureData asExperimentFeatureData(
            @Nullable ExperimentResultParams.ExperimentFeature experimentFeature,
            List<ExperimentResultParams.ExperimentTaskFeature> taskFeatures) {

        final ExperimentFeatureData featureData = new ExperimentFeatureData();

        if (experimentFeature==null) {
            featureData.execStatus = FeatureExecStatus.finished_with_errors.code;
            featureData.execStatusAsString = FeatureExecStatus.finished_with_errors.info;
            return featureData;
        }

        BeanUtils.copyProperties(experimentFeature, featureData, "variables");
        featureData.variables.addAll(experimentFeature.variables);
        featureData.maxValues = experimentFeature.maxValues;
        featureData.execStatusAsString = FeatureExecStatus.finished.info;
        return featureData;
    }

    @SuppressWarnings("Duplicates")
    private void fixData(PlotData data) {
        if (data.x.size()==1) {
            data.x.add("stub-x");
            BigDecimal[][] z = new BigDecimal[data.z.length][2];
            for (int i = 0; i < data.z.length; i++) {
                z[i][0] = data.z[i][0];
                z[i][1] = BigDecimal.ZERO;
            }
            data.z = z;
        }
        if (data.y.size()==1) {
            data.y.add("stub-y");
            BigDecimal[][] z = new BigDecimal[2][data.z[0].length];
            for (int i = 0; i < data.z[0].length; i++) {
                z[0][i] = data.z[0][i];
                z[1][i] = BigDecimal.ZERO;
            }
            data.z = z;
        }
    }

    private PlotData findExperimentTaskForPlot(
            Long experimentResultId, ExperimentResultParamsYamlWithCache apywc, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (apywc.experimentResult.features.isEmpty() ) {
            return EMPTY_PLOT_DATA;
        }
        List<ExperimentResultTaskParams> selected = getTasksForFeatureIdAndParams(experimentResultId, apywc.experimentResult, feature, params);
        return collectDataForPlotting(apywc, selected, paramsAxis);
    }

    private List<ExperimentResultTaskParams> getTasksForFeatureIdAndParams(
            Long experimentResultId, ExperimentResultParams experimentResult, ExperimentFeature feature, String[] params) {
        final Map<Long, Integer> taskToTaskType = experimentResult.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(feature.getId()))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        final Set<Long> taskIds = taskToTaskType.keySet();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<ExperimentTask> experimentTasks = experimentTaskRepository.findTasksById(experimentResultId, taskIds);
        List<ExperimentResultTaskParams> selected = experimentTasks.stream()
                .map(o-> ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                .filter(atpy -> atpy.execState > 1)
                .collect(Collectors.toList());

        if (!isEmpty(params)) {
            selected = filterTasks(experimentResult, params, selected);
        }
        return selected;
    }

    private static PlotData collectDataForPlotting(ExperimentResultParamsYamlWithCache estb, List<ExperimentResultTaskParams> selected, String[] paramsAxis) {
        final PlotData data = new PlotData();
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
        for (ExperimentResultTaskParams task : selected) {

            if (metricKey==null) {
                for (Map.Entry<String, BigDecimal> entry : task.metrics.values.entrySet()) {
                    metricKey = entry.getKey();
                    break;
                }
            }

            Integer idxX1 = mapX.get(task.taskParams.inline.get(paramCleared.get(0)));
            int idxX = idxX1 != null ? idxX1 : 0;
            Integer idxY1 = mapY.get(task.taskParams.inline.get(paramCleared.get(1)));
            int idxY = idxY1!=null ? idxY1 : 0;

            data.z[idxY][idxX] = data.z[idxY][idxX].add(task.metrics.values.get(metricKey));
        }

        return data;
    }


    private static List<ExperimentResultTaskParams> filterTasks(ExperimentResultParams epy, String[] params, List<ExperimentResultTaskParams> tasks) {
        final Set<String> paramSet = new HashSet<>();
        final Set<String> paramFilterKeys = new HashSet<>();
        for (String param : params) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            paramSet.add(param);
            paramFilterKeys.add(ParamFilter.of(param).key);
        }
        final Map<String, Map<String, Integer>> paramByIndex = ExperimentResultService.getHyperParamsAsMap(epy.hyperParams);

        List<ExperimentResultTaskParams> selected = new ArrayList<>();
        for (ExperimentResultTaskParams task : tasks) {
            if (task.taskParams.inline.isEmpty()) {
                continue;
            }
            boolean[] isOk = new boolean[task.taskParams.inline.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : task.taskParams.inline.entrySet()) {
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

    private static boolean isInclude(boolean[] isOk ) {
        for (boolean b : isOk) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(String[] params) {
        for (String param : params) {
            if (StringUtils.isNotBlank(param)) {
                return false;
            }
        }
        return true;
    }

    public ExperimentFeatureExtendedResult getExperimentFeatureExtended(long experimentResultId, Long experimentId, Long featureId) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentFeatureExtendedResult("#422.260 experiment wasn't found in experimentResult, id: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsJsonUtils.BASE_UTILS.to(experimentResult.params));
        } catch (YAMLException e) {
            final String es = "#422.270 Can't extract experiment from experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature experimentFeature = ypywc.getFeature(featureId);
        if (experimentFeature == null) {
            return new ExperimentFeatureExtendedResult("#422.280 feature wasn't found, experimentFeatureId: " + featureId);
        }

        ExperimentFeatureExtendedResult result = prepareExperimentFeatures(experimentResultId, ypywc, experimentFeature);
        return result;
    }

    // TODO 2019-09-11 need to add unit-test
    private ExperimentFeatureExtendedResult prepareExperimentFeatures(
            Long experimentResultId, ExperimentResultParamsYamlWithCache ypywc, final ExperimentFeature experimentFeature) {

        final Map<Long, Integer> taskToTaskType = ypywc.experimentResult.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(experimentFeature.id))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        List<Long> taskWIthTypes = ypywc.experimentResult.taskFeatures.stream()
                .filter(key -> taskToTaskType.containsKey(key.taskId))
                .sorted(Comparator.comparingLong(anotherLong -> anotherLong.taskId))
                .limit(Consts.PAGE_REQUEST_10_REC.getPageSize() + 1)
                .map(o->o.taskId)
                .collect(Collectors.toList());

        Slice<ExperimentResultTaskParams> tasks = new SliceImpl<>(
                taskWIthTypes.subList(0, Math.min(taskWIthTypes.size(), Consts.PAGE_REQUEST_10_REC.getPageSize()))
                        .stream()
                        .map(id-> experimentTaskRepository.findByExperimentResultIdAndTaskId(experimentResultId, id))
                        .filter(Objects::nonNull)
                        .map( o-> ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                        .collect(Collectors.toList()),
                Consts.PAGE_REQUEST_10_REC,
                taskWIthTypes.size()>10
        );

        ExecContextImpl execContext = new ExecContextImpl();
        execContext.setParams( ypywc.experimentResult.execContext.execContextParams);
        execContext.id = ypywc.experimentResult.execContext.execContextId;
        execContext.state = EnumsApi.ExecContextState.FINISHED.code;

        ExperimentFeatureExtendedResult result = new ExperimentFeatureExtendedResult();
        result.metricsResult = getMetricsResult(experimentFeature, ypywc.experimentResult.taskFeatures, taskToTaskType);
        result.hyperParamResult = getHyperParamResult(ypywc);
        result.tasks = tasks;
        result.consoleResult = new ConsoleResult();

        result.experimentFeature = asExperimentFeatureData(experimentFeature, ypywc.experimentResult.taskFeatures);

        return result;
    }

    @NonNull
    private MetricsResult getMetricsResult(ExperimentFeature feature, List<ExperimentTaskFeature> taskFeatures, Map<Long, Integer> taskToTaskType) {
        final MetricsResult metricsResult = new MetricsResult();

        metricsResult.metricNames.addAll(getMetricsNames(feature));

        taskFeatures.stream()
                .filter(o -> taskToTaskType.containsKey(o.taskId))
                .map(o -> {
                    MetricElement element = new MetricElement();
                    for (String metricName : metricsResult.metricNames) {
                        element.values.add(o.metrics.values.get(metricName));
                    }
                    return element;
                })
                .sorted(ExperimentService::compareMetricElement)
                .collect(Collectors.toCollection(()->metricsResult.metrics));

        return metricsResult;
    }

    public static List<String> getMetricsNames(ExperimentFeature feature) {
        //noinspection SimplifyStreamApiCallChains
        return feature.maxValues.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private static HyperParamResult getHyperParamResult(ExperimentResultParamsYamlWithCache ypywc) {
        HyperParamResult hyperParamResult = new HyperParamResult();
        for (HyperParam hyperParam : ypywc.experimentResult.hyperParams) {
            InlineVariableUtils.NumberOfVariants variants = InlineVariableUtils.getNumberOfVariants(hyperParam.getValues());
            HyperParamList list = new HyperParamList(hyperParam.getKey());
            for (String value : variants.values) {
                list.getList().add( new HyperParamElement(value, false));
            }
            if (list.getList().isEmpty()) {
                list.getList().add( new HyperParamElement("<Error value>", false));
            }
            hyperParamResult.getElements().add(list);
        }
        return hyperParamResult;
    }

    public ConsoleResult getTasksConsolePart(Long experimentResultId, Long taskId) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ConsoleResult("#422.300 experiment wasn't found in experimentResult, id: " + experimentResultId);
        }

        ExperimentTask task = experimentTaskRepository.findByExperimentResultIdAndTaskId(experimentResultId, taskId);
        if (task==null ) {
            return new ConsoleResult("#422.310 Can't find a console output");
        }
        ExperimentResultTaskParams atpy = ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(atpy.functionExecResults);
        if (functionExec ==null ) {
            return new ConsoleResult("#422.313 Can't find a console output");
        }
        return new ConsoleResult(functionExec.exec.exitCode, functionExec.exec.isOk, functionExec.exec.console);
    }

    public ExperimentFeatureExtendedResult getFeatureProgressPart(Long experimentResultId, Long featureId, String[] params, Pageable pageable) {
        ExperimentResult experimentResult = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (experimentResult == null) {
            return new ExperimentFeatureExtendedResult("#422.320 experiment wasn't found in ExperimentResult, id: " + experimentResultId);
        }

        ExperimentResultParamsYamlWithCache ypywc;
        try {
            ypywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsJsonUtils.BASE_UTILS.to(experimentResult.params));
        } catch (YAMLException e) {
            final String es = "#422.330 Can't extract experiment from experimentResult, error: " + e.toString();
            log.error(es, e);
            return new ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature feature = ypywc.getFeature(featureId);

        ExecContextImpl execContext = new ExecContextImpl();
        execContext.setParams(ypywc.experimentResult.execContext.execContextParams);
        execContext.id = ypywc.experimentResult.execContext.execContextId;
        execContext.state = EnumsApi.ExecContextState.FINISHED.code;

        ExperimentFeatureExtendedResult result = new ExperimentFeatureExtendedResult();
        result.tasks = feature==null ?  Page.empty() : findTasks(experimentResultId, ypywc, ControllerUtils.fixPageSize(10, pageable), feature, params);
        result.consoleResult = new ConsoleResult();

        result.experimentFeature = asExperimentFeatureData(feature, ypywc.experimentResult.taskFeatures);

        return result;
    }

    private Slice<ExperimentResultTaskParams> findTasks(Long experimentResultId, ExperimentResultParamsYamlWithCache estb, Pageable pageable, @Nullable ExperimentFeature feature, String[] params) {
        if (feature == null) {
            return Page.empty();
        }
        List<ExperimentResultTaskParams> selected = getTasksForFeatureIdAndParams(experimentResultId, estb.experimentResult, feature, params);
        List<ExperimentResultTaskParams> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));

        for (ExperimentResultTaskParams atpy : subList) {
            atpy.typeAsString = estb.experimentResult.taskFeatures.stream()
                    .filter(tf->tf.taskId.equals(atpy.taskId))
                    .map(tf->EnumsApi.ExperimentTaskType.from(tf.taskType))
                    .findFirst()
                    .orElse(EnumsApi.ExperimentTaskType.UNKNOWN)
                    .toString();
        }
        Slice<ExperimentResultTaskParams> slice = new PageImpl<>(subList, pageable, selected.size());
        return slice;
    }
}
