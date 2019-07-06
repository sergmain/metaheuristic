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

package ai.metaheuristic.ai.launchpad.experiment;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.snippet.SnippetCode;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@SuppressWarnings("WeakerAccess")
@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class ExperimentTopLevelService {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final TaskRepository taskRepository;
    private final WorkbookRepository workbookRepository;
    private final TaskPersistencer taskPersistencer;

    private final ExperimentCache experimentCache;
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;

    public static ExperimentApiData.SimpleExperiment asSimpleExperiment(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        return new ExperimentApiData.SimpleExperiment(params.experimentYaml.getName(), params.experimentYaml.getDescription(), params.experimentYaml.getCode(), params.experimentYaml.getSeed(), e.getId());
    }

    public static ExperimentApiData.ExperimentResult asExperimentResult(Experiment e) {
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(e), e.params);
    }

    public ExperimentApiData.ExperimentsResult getExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        ExperimentApiData.ExperimentsResult result = new ExperimentApiData.ExperimentsResult();
        final Slice<Experiment> experiments = experimentRepository.findAllByOrderByIdDesc(pageable);

        List<ExperimentApiData.ExperimentResult> experimentResults =
                experiments.stream().map(ExperimentTopLevelService::asExperimentResult).collect(Collectors.toList());

        result.items = new PageImpl<>(experimentResults, pageable, experimentResults.size() + (experiments.hasNext() ? 1 : 0) );
        return result;
    }

    public ExperimentApiData.ExperimentResult getExperiment(long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentResult("#285.010 experiment wasn't found, experimentId: " + experimentId );
        }
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(experiment), experiment.params);
    }

    public ExperimentApiData.PlotData getPlotData(Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        return experimentService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    public ExperimentApiData.ConsoleResult getTasksConsolePart(Long taskId) {
        ExperimentApiData.ConsoleResult result = new ExperimentApiData.ConsoleResult();
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task!=null) {
            SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            if (snippetExec!=null) {
                final SnippetApiData.SnippetExecResult execSnippetExecResult = snippetExec.getExec();
                result.items.add(new ExperimentApiData.ConsoleResult.SimpleConsoleOutput(
                        execSnippetExecResult.snippetCode, execSnippetExecResult.exitCode, execSnippetExecResult.isOk, execSnippetExecResult.console));
            }
            else {
                log.info("#285.020 snippetExec is null");
            }
        }
        return result;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult getFeatureProgressPart(Long experimentId, Long featureId, String[] params, Pageable pageable) {
        Experiment experiment= experimentCache.findById(experimentId);

        ExperimentParamsYaml.ExperimentFeature feature = experiment.getExperimentParamsYaml().getFeature(featureId);

        TaskApiData.TasksResult tasksResult = new TaskApiData.TasksResult();
        tasksResult.items = experimentService.findTasks(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        ExperimentApiData.ExperimentFeatureExtendedResult result = new ExperimentApiData.ExperimentFeatureExtendedResult();
        result.tasksResult = tasksResult;
        result.experiment = ExperimentService.asExperimentData(experiment);
        result.experimentFeature = ExperimentService.asExperimentFeatureData(feature);
        result.consoleResult = new ExperimentApiData.ConsoleResult();
        return result;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(Long experimentId, Long featureId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.030 experiment wasn't found, experimentId: " + experimentId);
        }
        if (experiment.workbookId==null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.040 workbookId is null");
        }

        ExperimentParamsYaml.ExperimentFeature feature = experiment.getExperimentParamsYaml().getFeature(featureId);
        if (feature == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.050 feature wasn't found, experimentFeatureId: " + featureId);
        }

        return experimentService.prepareExperimentFeatures(experiment, feature);
    }

    public ExperimentApiData.ExperimentInfoExtendedResult getExperimentInfo(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.060 experiment wasn't found, experimentId: " + id);
        }
        if (experiment.getWorkbookId() == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.070 experiment wasn't startet yet, experimentId: " + id);
        }
        Workbook workbook = workbookRepository.findById(experiment.getWorkbookId()).orElse(null);
        if (workbook == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.080 experiment has broken ref to workbook, experimentId: " + id);
        }
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        for (ExperimentParamsYaml.HyperParam hyperParams : epy.experimentYaml.hyperParams) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants( variants.status ? variants.count : 0 );
        }

        ExperimentApiData.ExperimentInfoExtendedResult result = new ExperimentApiData.ExperimentInfoExtendedResult();
        if (experiment.getWorkbookId()==null) {
            result.addInfoMessage("#285.090 A launch is disabled, dataset isn't assigned");
        }

        ExperimentApiData.ExperimentInfoResult experimentInfoResult = new ExperimentApiData.ExperimentInfoResult();
        final List<ExperimentParamsYaml.ExperimentFeature> experimentFeatures = epy.processing.features;
        experimentInfoResult.features = experimentFeatures.stream().map(ExperimentService::asExperimentFeatureData).collect(Collectors.toList());
        experimentInfoResult.workbook = workbook;
        experimentInfoResult.workbookExecState = EnumsApi.WorkbookExecState.toState(workbook.getExecState());
        result.experiment = ExperimentService.asExperimentData(experiment);
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public ExperimentApiData.ExperimentsEditResult editExperiment(@PathVariable Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentsEditResult("#285.100 experiment wasn't found, experimentId: " + id);
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        ExperimentApiData.SnippetResult snippetResult = new ExperimentApiData.SnippetResult();

        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        final List<String> snippetCodes = epy.getSnippetCodes();
        List<Snippet> experimentSnippets = snippetService.getSnippetsForCodes(snippetCodes);
        snippetResult.snippets = experimentSnippets.stream().map(es->
                new ExperimentApiData.ExperimentSnippetResult(
                        es.getId(), es.getVersion(), es.getCode(), es.type, experiment.id)).collect(Collectors.toList());

        final List<String> types = Arrays.asList(CommonConsts.FIT_TYPE, CommonConsts.PREDICT_TYPE);
        snippetResult.selectOptions = snippetService.getSelectOptions(snippets,
                snippetResult.snippets.stream().map(o -> new SnippetCode(o.getId(), o.getSnippetCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!types.contains(s.type) ) {
                        return true;
                    }
                    if (CommonConsts.FIT_TYPE.equals(s.type) && snippetService.hasFit(experimentSnippets)) {
                        return true;
                    }
                    else if (CommonConsts.PREDICT_TYPE.equals(s.type) && snippetService.hasPredict(experimentSnippets)) {
                        return true;
                    }
                    else return false;
                });

        snippetResult.sortSnippetsByOrder();
        ExperimentApiData.ExperimentsEditResult result = new ExperimentApiData.ExperimentsEditResult();

        ExperimentApiData.HyperParamsResult r = new ExperimentApiData.HyperParamsResult();
        r.items = epy.experimentYaml.getHyperParams().stream().map(ExperimentTopLevelService::asHyperParamData).collect(Collectors.toList());
        result.hyperParams = r;
        result.simpleExperiment = asSimpleExperiment(experiment);
        result.snippetResult = snippetResult;
        return result;
    }

    public static ExperimentApiData.HyperParamData asHyperParamData(ExperimentParamsYaml.HyperParam ehp) {
        return new ExperimentApiData.HyperParamData(ehp.getKey(), ehp.getValues(), ehp.getVariants());
    }

    public OperationStatusRest updateParamsAndSave(Experiment e, ExperimentParamsYaml params, String name, String description, int seed) {
        params.experimentYaml.name = StringUtils.strip(name);
        params.experimentYaml.code = e.code;
        params.experimentYaml.description = StringUtils.strip(description);
        params.experimentYaml.setSeed(seed ==0 ? 1 : seed);
        params.createdOn = System.currentTimeMillis();

        e.updateParams(params);

        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest addExperimentCommit(ExperimentApiData.ExperimentData experiment) {
        Experiment e = new Experiment();
        e.code = StringUtils.strip(experiment.getCode());

        ExperimentParamsYaml params = new ExperimentParamsYaml();
        return updateParamsAndSave(e, params, experiment.getName(), experiment.getDescription(), experiment.getSeed());
    }

    public OperationStatusRest editExperimentCommit(ExperimentApiData.SimpleExperiment simpleExperiment) {
        OperationStatusRest op = validate(simpleExperiment);
        if (op!=null) {
            return op;
        }

        Experiment e = experimentRepository.findByIdForUpdate(simpleExperiment.id);
        if (e == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.110 experiment wasn't found, experimentId: " + simpleExperiment.id);
        }
        e.code = StringUtils.strip(simpleExperiment.getCode());

        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        return updateParamsAndSave(e, params, simpleExperiment.getName(), simpleExperiment.getDescription(), simpleExperiment.getSeed());
    }

    private OperationStatusRest validate(ExperimentApiData.SimpleExperiment se) {
        if (StringUtils.isBlank(se.getName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.120 Name of experiment is blank.");
        }
        if (StringUtils.isBlank(se.getCode())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.130 Code of experiment is blank.");
        }
        if (StringUtils.isBlank(se.getDescription())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.140 Description of experiment is blank.");
        }
        return null;
    }

    public OperationStatusRest metadataAddCommit(Long experimentId, String key, String value) {
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.150 experiment wasn't found, experimentId: " + experimentId);
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.160 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        String keyFinal = key.trim();
        boolean isExist = epy.experimentYaml.getHyperParams().stream().map(ExperimentParamsYaml.HyperParam::getKey).anyMatch(keyFinal::equals);
        if (isExist) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.170 hyper parameter "+key+" already exist");
        }

        ExperimentParamsYaml.HyperParam m = new ExperimentParamsYaml.HyperParam();
        m.setKey(keyFinal);
        m.setValues(value.trim());
        epy.experimentYaml.hyperParams.add(m);
        experiment.updateParams(epy);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataEditCommit(Long experimentId, String key, String value) {
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.180 experiment wasn't found, id: "+experimentId );
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.190 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        ExperimentParamsYaml.HyperParam m=null;
        String keyFinal = key.trim();
        for (ExperimentParamsYaml.HyperParam hyperParam : epy.experimentYaml.getHyperParams()) {
            if (hyperParam.getKey().equals(keyFinal)) {
                m = hyperParam;
                break;
            }
        }
        if (m==null) {
            m = new ExperimentParamsYaml.HyperParam();
            m.setKey(keyFinal);
            epy.experimentYaml.getHyperParams().add(m);
        }
        m.setValues(value.trim());
        experiment.updateParams(epy);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest snippetAddCommit(Long id, String snippetCode) {
        Experiment experiment = experimentRepository.findByIdForUpdate(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.200 experiment wasn't found, id: "+id );
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        Snippet s = snippetRepository.findByCode(snippetCode);
        if (s==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.210 snippet wasn't found, id: "+id );

        }
        switch(s.getType()){
            case "fit":
                epy.experimentYaml.fitSnippet = snippetCode;
                break;
            case "predict":
                epy.experimentYaml.predictSnippet = snippetCode;
                break;
            default:
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.220 snippet has non-supported type for an experiment: "+s.getType() );
        }
        experiment.updateParams(epy);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataDeleteCommit(long experimentId, String key) {
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.230 experiment wasn't found, id: "+experimentId );
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        for (int i = 0; i < epy.experimentYaml.hyperParams.size(); i++) {
            ExperimentParamsYaml.HyperParam hyperParam = epy.experimentYaml.hyperParams.get(i);
            if (hyperParam.key.equals(key)) {
                epy.experimentYaml.hyperParams.remove(i);
                break;
            }
        }
        experiment.updateParams(epy);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataDefaultAddCommit(long experimentId) {
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.240 experiment wasn't found, id: "+experimentId );
        }

        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        add(epy, "epoch", "[10]");
        add(epy, "RNN", "[LSTM, GRU, SimpleRNN]");
        add(epy, "activation", "[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]");
        add(epy, "optimizer", "[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]");
        add(epy, "batch_size", "[20, 40, 60]");
        add(epy, "time_steps", "[5, 40, 60]");
        add(epy, "metrics_functions", "['#in_top_draw_digit, accuracy', 'accuracy']");

        experiment.updateParams(epy);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private void add(ExperimentParamsYaml epy, String key, String value) {
        ExperimentParamsYaml.HyperParam param = new ExperimentParamsYaml.HyperParam(key, value, null);
        List<ExperimentParamsYaml.HyperParam> params = epy.experimentYaml.getHyperParams();
        for (ExperimentParamsYaml.HyperParam p1 : params) {
            if (p1.getKey().equals(param.getKey())) {
                p1.setValues(param.getValues());
                return;
            }
        }
        params.add(param);
    }

    public OperationStatusRest snippetDeleteCommit(long experimentId, String snippetCode) {
        if (snippetCode==null || snippetCode.isBlank()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.62 snippetCode is blank");
        }
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.250 experiment wasn't found, id: "+experimentId );
        }

        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        if (Objects.equals(epy.experimentYaml.fitSnippet, snippetCode)) {
            epy.experimentYaml.fitSnippet = null;
        }
        else if (Objects.equals(epy.experimentYaml.predictSnippet, snippetCode)) {
            epy.experimentYaml.predictSnippet = null;
        }
        experiment.updateParams(epy);
        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentDeleteCommit(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.260 experiment wasn't found, experimentId: " + id);
        }
        experimentCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentCloneCommit(Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.270 experiment wasn't found, experimentId: " + id);
        }
        // do not use experiment.getExperimentParamsYaml() because it's  caching ExperimentParamsYaml
        ExperimentParamsYaml epy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experiment.params);

        epy.createdOn = System.currentTimeMillis();

        final Experiment e = new Experiment();
        String newCode = StrUtils.incCopyNumber(experiment.getCode());
        int i = 0;
        while ( experimentRepository.findIdByCode(newCode)!=null  ) {
            newCode = StrUtils.incCopyNumber(newCode);
            if (i++>100) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.273 Can't find new code for experiment with code: " + experiment.getCode());
            }
        }
        epy.experimentYaml.code = newCode;
        e.code = newCode;
        e.updateParams(epy);
        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest rerunTask(long taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.280 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }
        Workbook workbook = workbookRepository.findById(task.getWorkbookId()).orElse(null);
        if (workbook == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.290 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
        }

        Task t = taskPersistencer.resetTask(taskId);
        return t!=null
                ? OperationStatusRest.OPERATION_STATUS_OK
                : new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.300 Can't re-run task #"+taskId+", see log for more information");
    }

    public OperationStatusRest uploadExperiment(MultipartFile file) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.310 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.320 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.330 only '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        try {
            File tempDir = DirUtils.createTempDir("mh-experiment-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.340 can't create temporary directory in " + location);
            }
            final File planFile = new File(tempDir, "experiment" + ext);
            log.debug("Start storing an uploaded experiment to disk");
            try(OutputStream os = new FileOutputStream(planFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start loading experiment into db");
            String yaml = FileUtils.readFileToString(planFile, StandardCharsets.UTF_8);
            OperationStatusRest result = addExperiment(yaml);

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages, result.infoMessages);
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("#285.350 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.360 can't load plans, Error: " + e.toString());
        }
    }

    public OperationStatusRest addExperiment(String experimentYamlAsStr) {
        if (StringUtils.isBlank(experimentYamlAsStr)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.017 plan yaml is empty");
        }

        ExperimentParamsYaml ppy;
        try {
            ppy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experimentYamlAsStr);
        } catch (WrongVersionOfYamlFileException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.017 Error parsing yaml: " + e.getMessage());
        }

        final String code = ppy.experimentYaml.code;
        if (StringUtils.isBlank(code)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.020 code of experiment is empty");
        }
        ppy.createdOn = System.currentTimeMillis();

        Long experimentId = experimentRepository.findIdByCode(code);
        if (experimentId!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.033 plan with such code already exists, code: " + code);
        }

        Experiment e = new Experiment();
        e.code = ppy.experimentYaml.code;
        e.updateParams(ppy);

        experimentCache.save(e);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

}
