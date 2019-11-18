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

package ai.metaheuristic.ai.launchpad.atlas;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.ai.launchpad.beans.Atlas;
import ai.metaheuristic.ai.launchpad.beans.AtlasTask;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.data.AtlasData;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentUtils;
import ai.metaheuristic.ai.launchpad.repositories.AtlasRepository;
import ai.metaheuristic.ai.launchpad.repositories.AtlasTaskRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlWithCache;
import ai.metaheuristic.ai.yaml.atlas.AtlasTaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.atlas.AtlasTaskParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
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
import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.HyperParam;

@SuppressWarnings("Duplicates")
@Slf4j
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasTopLevelService {

    private static final String ZIP_DIR = "zip";
    private static final String TASKS_DIR = "tasks";
    private static final String EXPERIMENT_YAML_FILE = "experiment.yaml";
    private static final String TASK_YAML_FILE = "task-%s.yaml";

    private final AtlasRepository atlasRepository;
    private final AtlasTaskRepository atlasTaskRepository;
    private final AtlasParamsYamlUtils atlasParamsYamlUtils;
    private final WorkbookService workbookService;

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
        File resultDir = DirUtils.createTempDir("import-result-to-atlas-");
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

            Atlas atlas = new Atlas();
            LocalDate date = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
            String dateAsStr = date.format(formatter);

            atlas.name = "experiment uploaded on " + dateAsStr;
            atlas.description = atlas.name;
            atlas.code = atlas.name;
            atlas.params = params;
            atlas = atlasRepository.save(atlas);

            AtlasParamsYaml apy = atlasParamsYamlUtils.BASE_YAML_UTILS.to(params);
            int count = 0;
            for (Long taskId : apy.taskIds) {
                if (++count%100==0) {
                    log.info("#422.045 Current number of imported task: {} of total {}", count, apy.taskIds.size());
                }
                File taskFile = new File(tasksDir, S.f(TASK_YAML_FILE, taskId));

                AtlasTask at = new AtlasTask();
                at.atlasId = atlas.id;
                at.taskId = taskId;
                at.params = FileUtils.readFileToString(taskFile, StandardCharsets.UTF_8);
                atlasTaskRepository.save(at);
            }
        }
        catch (Exception e) {
            log.error("#422.040 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.050 can't load snippets, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(resultDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ResponseEntity<AbstractResource> exportAtlasToFile(Long atlasId) {
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
        File zipFile = new File(resultDir, S.f("export-%s.zip", atlasId));
        if (zipFile.isDirectory()) {
            log.error("#422.080 Error, path for zip file is actually directory, path: {}", zipFile.getAbsolutePath());
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.NOT_FOUND);
        }
        File exportFile = new File(zipDir, EXPERIMENT_YAML_FILE);
        try {
            FileUtils.write(exportFile, atlas.params, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("#422.090 Error", e);
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Set<Long> atlasTaskIds = atlasTaskRepository.findIdsByAtlasId(atlasId);

        AtlasParamsYaml apy = atlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params);
        if (atlasTaskIds.size()!=apy.taskIds.size()) {
            log.warn("numbers of tasks in params of stored experiment and in db are different, " +
                    "atlasTaskIds.size: {}, apy.taskIds.size: {}", atlasTaskIds.size(), apy.taskIds.size());
        }

        int count = 0;
        for (Long atlasTaskId : atlasTaskIds) {
            if (++count%100==0) {
                log.info("#422.095 Current number of exported task: {} of total {}", count, atlasTaskIds.size());
            }
            AtlasTask at = atlasTaskRepository.findById(atlasTaskId).orElse(null);
            if (at==null) {
                log.error("#422.100 AtlasTask wasn't found for is #{}", atlasTaskId);
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

    public AtlasData.ExperimentDataOnly getExperimentDataOnly(Long atlasId) {

        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentDataOnly("#422.120 experiment wasn't found in atlas, atlasId: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(atlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params, atlasId));
        } catch (YAMLException e) {
            String es = "#422.130 Can't parse an atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentDataOnly(es);
        }
        if (ypywc.atlasParams.experiment == null) {
            return new AtlasData.ExperimentDataOnly("#422.140 experiment wasn't found, experimentId: " + atlasId);
        }
        if (ypywc.atlasParams.workbook == null) {
            return new AtlasData.ExperimentDataOnly("#422.150 experiment has broken ref to workbook, experimentId: " + atlasId);
        }
        if (ypywc.atlasParams.workbook.workbookId==null ) {
            return new AtlasData.ExperimentDataOnly("#422.160 experiment wasn't startet yet, experimentId: " + atlasId);
        }

        ExperimentApiData.ExperimentData experiment = new ExperimentApiData.ExperimentData();
        experiment.id = ypywc. atlasParams.experiment.experimentId;
        experiment.workbookId = ypywc.atlasParams.workbook.workbookId;

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        experiment.code = epy.experimentYaml.code;
        experiment.name = epy.experimentYaml.name;
        experiment.description = epy.experimentYaml.description;
        experiment.seed = epy.experimentYaml.seed;
        experiment.isAllTaskProduced = epy.processing.isAllTaskProduced;
        experiment.isFeatureProduced = epy.processing.isFeatureProduced;
        experiment.createdOn = epy.createdOn;
        experiment.numberOfTask = epy.processing.numberOfTask;
        experiment.hyperParams = epy.experimentYaml.hyperParams;

        AtlasData.ExperimentDataOnly result = new AtlasData.ExperimentDataOnly();
        if (experiment.getWorkbookId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }

        result.experiment = experiment;
        result.atlasId = atlas.id;
        return result;
    }

    public AtlasData.ExperimentInfoExtended getExperimentInfoExtended(Long atlasId) {

        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentInfoExtended("#422.170 experiment wasn't found in atlas, atlasId: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(atlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params, atlasId));
        } catch (YAMLException e) {
            String es = "#422.180 Can't parse an atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentInfoExtended(es);
        }
        if (ypywc.atlasParams.experiment == null) {
            return new AtlasData.ExperimentInfoExtended("#422.190 experiment wasn't found, experimentId: " + atlasId);
        }
        if (ypywc.atlasParams.workbook == null) {
            return new AtlasData.ExperimentInfoExtended("#422.200 experiment has broken ref to workbook, experimentId: " + atlasId);
        }
        if (ypywc.atlasParams.workbook.workbookId==null ) {
            return new AtlasData.ExperimentInfoExtended("#422.210 experiment wasn't startet yet, experimentId: " + atlasId);
        }

        ExperimentApiData.ExperimentData experiment = new ExperimentApiData.ExperimentData();
        experiment.id = ypywc. atlasParams.experiment.experimentId;
        experiment.workbookId = ypywc.atlasParams.workbook.workbookId;

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        experiment.code = epy.experimentYaml.code;
        experiment.name = epy.experimentYaml.name;
        experiment.description = epy.experimentYaml.description;
        experiment.seed = epy.experimentYaml.seed;
        experiment.isAllTaskProduced = epy.processing.isAllTaskProduced;
        experiment.isFeatureProduced = epy.processing.isFeatureProduced;
        experiment.createdOn = epy.createdOn;
        experiment.numberOfTask = epy.processing.numberOfTask;
        experiment.hyperParams = epy.experimentYaml.hyperParams;



        for (HyperParam hyperParams : ypywc.getExperimentParamsYaml().experimentYaml.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants(variants.status ? variants.count : 0);
        }

        AtlasData.ExperimentInfoExtended result = new AtlasData.ExperimentInfoExtended();
        if (experiment.getWorkbookId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }
        result.atlas = atlas;

        WorkbookImpl workbook = new WorkbookImpl();
        workbook.params = ypywc.atlasParams.workbook.workbookParams;
        workbook.id = ypywc.atlasParams.workbook.workbookId;
        workbook.execState = ypywc.atlasParams.workbook.execState;
        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookService.findAll(workbook);

        AtlasData.ExperimentInfo experimentInfoResult = new AtlasData.ExperimentInfo();
        experimentInfoResult.features = ypywc.getExperimentParamsYaml().processing.features
                .stream()
                .map(e -> ExperimentService.asExperimentFeatureData(e, taskVertices, epy.processing.taskFeatures)).collect(Collectors.toList());

        experimentInfoResult.workbook = workbook;
        experimentInfoResult.workbookExecState = EnumsApi.WorkbookExecState.toState(workbook.execState);

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public OperationStatusRest atlasDeleteCommit(Long id) {
        Long atlasId = atlasRepository.findIdById(id);
        if (atlasId == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.220 experiment wasn't found in atlas, id: " + id);
        }
        final AtomicBoolean isFound = new AtomicBoolean();
        do {
            isFound.set(false);
            atlasTaskRepository.findAllAsTaskSimple(PageRequest.of(0, 10), atlasId)
                    .forEach(atlasTaskId -> {
                        isFound.set(true);
                        atlasTaskRepository.deleteById(atlasTaskId);
                    });
        } while (isFound.get());
        atlasRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }


    public AtlasData.PlotData getPlotData(Long atlasId, Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.PlotData("#422.230 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(atlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params, atlasId));
        } catch (YAMLException e) {
            String es = "#422.240 Can't parse an atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.PlotData(es);
        }
        ExperimentFeature feature = ypywc.getFeature(featureId);
        if (feature==null) {
            return AtlasData.EMPTY_PLOT_DATA;
        }
        AtlasData.PlotData data = findExperimentTaskForPlot(atlasId, ypywc, feature, params, paramsAxis);
        // TODO 2019-07-23 right now 2D lines plot isn't working. need to investigate
        //  so it'll be 3D with a fake zero data
        fixData(data);
        return data;
    }

    @SuppressWarnings("Duplicates")
    private void fixData(AtlasData.PlotData data) {
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

    private AtlasData.PlotData findExperimentTaskForPlot(
            Long atlasId, AtlasParamsYamlWithCache apywc, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (apywc.atlasParams.experiment == null || apywc.getExperimentParamsYaml().processing.features == null ) {
            return AtlasData.EMPTY_PLOT_DATA;
        } else {
            List<AtlasTaskParamsYaml> selected = getTasksForFeatureIdAndParams(atlasId, apywc, feature, params);
            return collectDataForPlotting(apywc, selected, paramsAxis);
        }
    }

    private List<AtlasTaskParamsYaml> getTasksForFeatureIdAndParams(Long atlasId, AtlasParamsYamlWithCache estb1, ExperimentFeature feature, String[] params) {
        final Map<Long, Integer> taskToTaskType = estb1.getExperimentParamsYaml().processing.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(feature.getId()))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        final Set<Long> taskIds = taskToTaskType.keySet();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<AtlasTask> atlasTasks = atlasTaskRepository.findTasksById(atlasId, taskIds);
        List<AtlasTaskParamsYaml> selected = atlasTasks.stream()
                .map(o->AtlasTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                .filter(atpy -> atpy.execState > 1)
                .collect(Collectors.toList());

        if (!isEmpty(params)) {
            selected = filterTasks(estb1.getExperimentParamsYaml(), params, selected);
        }
        return selected;
    }

    private AtlasData.PlotData collectDataForPlotting(AtlasParamsYamlWithCache estb, List<AtlasTaskParamsYaml> selected, String[] paramsAxis) {
        final AtlasData.PlotData data = new AtlasData.PlotData();
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
        for (AtlasTaskParamsYaml task : selected) {

            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(task.metrics) );
            if (metricValues==null) {
                continue;
            }
            if (metricKey==null) {
                //noinspection LoopStatementThatDoesntLoop
                for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                    metricKey = entry.getKey();
                    break;
                }
            }

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.taskParams);
            int idxX = mapX.get(taskParamYaml.taskYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.taskYaml.hyperParams.get(paramCleared.get(1)));
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }


    private List<AtlasTaskParamsYaml> filterTasks(ExperimentParamsYaml epy, String[] params, List<AtlasTaskParamsYaml> tasks) {
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

        List<AtlasTaskParamsYaml> selected = new ArrayList<>();
        for (AtlasTaskParamsYaml task : tasks) {
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.taskParams);
            boolean[] isOk = new boolean[taskParamYaml.taskYaml.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.taskYaml.hyperParams.entrySet()) {
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

    public AtlasData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(long atlasId, Long experimentId, Long featureId) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#422.260 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(atlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params, atlasId));
        } catch (YAMLException e) {
            final String es = "#422.270 Can't extract experiment from atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature experimentFeature = ypywc.getFeature(featureId);
        if (experimentFeature == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#422.280 feature wasn't found, experimentFeatureId: " + featureId);
        }

        AtlasData.ExperimentFeatureExtendedResult result = prepareExperimentFeatures(atlasId, ypywc, experimentFeature);
        if (result==null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#422.290 can't prepare experiment data");
        }
        return result;
    }

    // TODO 2019-09-11 need to add unit-test
    private AtlasData.ExperimentFeatureExtendedResult prepareExperimentFeatures(
            Long atlasId, AtlasParamsYamlWithCache ypywc, final ExperimentFeature experimentFeature) {

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        final Map<Long, Integer> taskToTaskType = epy.processing.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(experimentFeature.id))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        List<Long> taskWIthTypes = ypywc.atlasParams.taskIds.stream()
                .filter(taskToTaskType::containsKey)
                .sorted(Long::compareTo)
                .limit(Consts.PAGE_REQUEST_10_REC.getPageSize() + 1)
                .collect(Collectors.toList());

        Slice<AtlasTaskParamsYaml> tasks = new SliceImpl<>(
                taskWIthTypes.subList(0, Math.min(taskWIthTypes.size(), Consts.PAGE_REQUEST_10_REC.getPageSize()))
                        .stream()
                        .map(id-> atlasTaskRepository.findByAtlasIdAndTaskId(atlasId, id))
                        .filter(Objects::nonNull)
                        .map( o-> AtlasTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                        .collect(Collectors.toList()),
                Consts.PAGE_REQUEST_10_REC,
                taskWIthTypes.size()>10
        );

        AtlasData.HyperParamResult hyperParamResult = new AtlasData.HyperParamResult();
        for (HyperParam hyperParam : epy.experimentYaml.getHyperParams()) {
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues());
            ExperimentApiData.HyperParamList list = new ExperimentApiData.HyperParamList(hyperParam.getKey());
            for (String value : variants.values) {
                list.getList().add( new ExperimentApiData.HyperParamElement(value, false));
            }
            if (list.getList().isEmpty()) {
                list.getList().add( new ExperimentApiData.HyperParamElement("<Error value>", false));
            }
            hyperParamResult.getElements().add(list);
        }

        final AtlasData.MetricsResult metricsResult = new AtlasData.MetricsResult();
        final List<Map<String, BigDecimal>> values = new ArrayList<>();

        tasks.stream()
                .filter(o->taskToTaskType.containsKey(o.taskId) && o.execState > 1)
                .forEach( o-> {
                    MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(o.metrics) );
                    if (metricValues==null) {
                        return;
                    }
                    for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                        metricsResult.metricNames.add(entry.getKey());
                    }
                    values.add(metricValues.values);

                });

        List<AtlasData.MetricElement> elements = new ArrayList<>();
        for (Map<String, BigDecimal> value : values) {
            AtlasData.MetricElement element = new AtlasData.MetricElement();
            for (String metricName : metricsResult.metricNames) {
                element.values.add(value.get(metricName));
            }
            elements.add(element);
        }
        elements.sort(ExperimentService::compareMetricElement);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

        WorkbookImpl workbook = new WorkbookImpl();
        workbook.params = ypywc.atlasParams.workbook.workbookParams;
        workbook.id = ypywc.atlasParams.workbook.workbookId;
        workbook.execState = ypywc.atlasParams.workbook.execState;
        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookService.findAll(workbook);

        AtlasData.ExperimentFeatureExtendedResult result = new AtlasData.ExperimentFeatureExtendedResult();
        result.metricsResult = metricsResult;
        result.hyperParamResult = hyperParamResult;
        result.tasks = tasks;
        result.experimentFeature = ExperimentService.asExperimentFeatureData(experimentFeature, taskVertices, epy.processing.taskFeatures);
        result.consoleResult = new AtlasData.ConsoleResult();

        return result;
    }

    public AtlasData.ConsoleResult getTasksConsolePart(Long atlasId, Long taskId) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ConsoleResult("#422.300 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasTask task = atlasTaskRepository.findByAtlasIdAndTaskId(atlasId, taskId);
        if (task==null ) {
            return new AtlasData.ConsoleResult("#422.310 Can't find a console output");
        }
        AtlasTaskParamsYaml atpy = AtlasTaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(atpy.snippetExecResults);
        if (snippetExec==null ) {
            return new AtlasData.ConsoleResult("#422.313 Can't find a console output");
        }
        return new AtlasData.ConsoleResult(snippetExec.exec.exitCode, snippetExec.exec.isOk, snippetExec.exec.console);
    }

    public AtlasData.ExperimentFeatureExtendedResult getFeatureProgressPart(Long atlasId, Long featureId, String[] params, Pageable pageable) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#422.320 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(atlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params, atlasId));
        } catch (YAMLException e) {
            final String es = "#422.330 Can't extract experiment from atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature feature = ypywc.getFeature(featureId);

        WorkbookImpl workbook = new WorkbookImpl();
        workbook.params = ypywc.atlasParams.workbook.workbookParams;
        workbook.id = ypywc.atlasParams.workbook.workbookId;
        workbook.execState = ypywc.atlasParams.workbook.execState;
        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookService.findAll(workbook);

        AtlasData.ExperimentFeatureExtendedResult result = new AtlasData.ExperimentFeatureExtendedResult();
        result.tasks = findTasks(atlasId, ypywc, ControllerUtils.fixPageSize(10, pageable), feature, params);
        result.experimentFeature = ExperimentService.asExperimentFeatureData(feature, taskVertices, ypywc.getExperimentParamsYaml().processing.taskFeatures);
        result.consoleResult = new AtlasData.ConsoleResult();
        return result;
    }

    private Slice<AtlasTaskParamsYaml> findTasks(Long atlasId, AtlasParamsYamlWithCache estb, Pageable pageable, ExperimentFeature feature, String[] params) {
        if (feature == null) {
            return Page.empty();
        }
        List<AtlasTaskParamsYaml> selected = getTasksForFeatureIdAndParams(atlasId, estb, feature, params);
        List<AtlasTaskParamsYaml> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));

        ExperimentParamsYaml epy = estb.getExperimentParamsYaml();

        for (AtlasTaskParamsYaml atpy : subList) {
            atpy.typeAsString = epy.processing.taskFeatures.stream()
                    .filter(tf->tf.taskId.equals(atpy.taskId))
                    .map(tf->EnumsApi.ExperimentTaskType.from(tf.taskType))
                    .findFirst()
                    .orElse(EnumsApi.ExperimentTaskType.UNKNOWN)
                    .toString();
        }
        //noinspection UnnecessaryLocalVariable
        Slice<AtlasTaskParamsYaml> slice = new PageImpl<>(subList, pageable, selected.size());
        return slice;
    }
}
