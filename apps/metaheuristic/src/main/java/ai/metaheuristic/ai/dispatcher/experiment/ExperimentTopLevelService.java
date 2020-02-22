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

package ai.metaheuristic.ai.mh.dispatcher..experiment;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mh.dispatcher..DispatcherContext;
import ai.metaheuristic.ai.mh.dispatcher..beans.*;
import ai.metaheuristic.ai.mh.dispatcher..data.FunctionData;
import ai.metaheuristic.ai.mh.dispatcher..source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.mh.dispatcher..repositories.ExperimentRepository;
import ai.metaheuristic.ai.mh.dispatcher..repositories.FunctionRepository;
import ai.metaheuristic.ai.mh.dispatcher..repositories.TaskRepository;
import ai.metaheuristic.ai.mh.dispatcher..function.FunctionService;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextCache;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextFSM;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.mh.dispatcher..ExecContext;
import ai.metaheuristic.api.mh.dispatcher..Task;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@SuppressWarnings("WeakerAccess")
@Service
@Slf4j
@Profile("mh.dispatcher.")
@RequiredArgsConstructor
public class ExperimentTopLevelService {

    private final List<String> experimentFunctionTypes = Arrays.asList(CommonConsts.FIT_TYPE, CommonConsts.PREDICT_TYPE, CommonConsts.CHECK_FITTING_TYPE);

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionService functionService;
    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;

    private final ExperimentCache experimentCache;
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final ExecContextService execContextService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public static ExperimentApiData.SimpleExperiment asSimpleExperiment(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        return new ExperimentApiData.SimpleExperiment(params.experimentYaml.getName(), params.experimentYaml.getDescription(), params.experimentYaml.getCode(), params.experimentYaml.getSeed(), e.getId());
    }

    public static ExperimentApiData.ExperimentResult asExperimentResultShort(Experiment e) {
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentDataShort(e), null);
    }

    public ExperimentApiData.ExperimentsResult getExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        ExperimentApiData.ExperimentsResult result = new ExperimentApiData.ExperimentsResult();
        final Slice<Long> experimentIds = experimentRepository.findAllByOrderByIdDesc(pageable);

        List<ExperimentApiData.ExperimentResult> experimentResults =
                experimentIds.stream()
                        .map(experimentCache::findById)
                        .map(ExperimentTopLevelService::asExperimentResultShort)
                        .collect(Collectors.toList());

        result.items = new PageImpl<>(experimentResults, pageable, experimentResults.size() + (experimentIds.hasNext() ? 1 : 0) );
        return result;
    }

    public ExperimentApiData.ExperimentResult getExperimentWithoutProcessing(long experimentId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentResult("#285.010 experiment wasn't found, experimentId: " + experimentId );
        }
        ExperimentParamsYaml epy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experiment.getParams());
        epy.processing = null; //new ExperimentParamsYaml.ExperimentProcessing();
        String params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.toString(epy);

        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(experiment), params);
    }

    public ExperimentApiData.PlotData getPlotData(Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        return experimentService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    public ExperimentApiData.ConsoleResult getTasksConsolePart(Long taskId) {
        ExperimentApiData.ConsoleResult result = new ExperimentApiData.ConsoleResult();
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task!=null) {
            FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.getFunctionExecResults());
            if (functionExec !=null) {
                final FunctionApiData.SystemExecResult execSystemExecResult = functionExec.getExec();
                result.items.add(new ExperimentApiData.ConsoleResult.SimpleConsoleOutput(
                        execSystemExecResult.functionCode, execSystemExecResult.exitCode, execSystemExecResult.isOk, execSystemExecResult.console));
            }
            else {
                log.info("#285.020 functionExec is null");
            }
        }
        return result;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult getFeatureProgressPart(Long experimentId, Long featureId, String[] params, Pageable pageable) {
        Experiment experiment= experimentCache.findById(experimentId);
        ExecContextImpl execContext = execContextCache.findById(experiment.execContextId);

        ExperimentParamsYaml.ExperimentFeature feature = experiment.getExperimentParamsYaml().getFeature(featureId);

        TaskApiData.TasksResult tasksResult = new TaskApiData.TasksResult();
        tasksResult.items = experimentService.findTasks(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        List<ExecContextParamsYaml.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(execContext);

        ExperimentApiData.ExperimentFeatureExtendedResult result = new ExperimentApiData.ExperimentFeatureExtendedResult();
        result.tasksResult = tasksResult;
        result.experiment = ExperimentService.asExperimentData(experiment);
        result.experimentFeature = ExperimentService.asExperimentFeatureData(feature, taskVertices, experiment.getExperimentParamsYaml().processing.taskFeatures);
        result.consoleResult = new ExperimentApiData.ConsoleResult();
        return result;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(Long experimentId, Long featureId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.030 experiment wasn't found, experimentId: " + experimentId);
        }
        return experimentService.prepareExperimentFeatures(experiment, featureId);
    }

    public ExperimentApiData.ExperimentInfoExtendedResult getExperimentInfo(Long experimentId) {

        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.060 experiment wasn't found, experimentId: " + experimentId);
        }
        // TODO 2019-07-21 calculation of max shouldn't be called every time. Add button for recalculating?
//        experimentService.updateMaxValueForExperimentFeatures(experiment.getExecContextId());

        // one more time to get new object from cache
        experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.064 experiment wasn't found, experimentId: " + experimentId);
        }

        if (experiment.getExecContextId() == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.070 experiment wasn't startet yet, experimentId: " + experimentId);
        }

        ExecContextImpl execContext = execContextCache.findById(experiment.getExecContextId());
        if (execContext == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.080 experiment has broken ref to execContext, experimentId: " + experimentId);
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
        if (experiment.getExecContextId()==null) {
            result.addInfoMessage("#285.090 A launch is disabled, dataset isn't assigned");
        }

        List<ExecContextParamsYaml.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(execContext);
        ExperimentApiData.ExperimentInfoResult experimentInfoResult = new ExperimentApiData.ExperimentInfoResult();
        final List<ExperimentParamsYaml.ExperimentFeature> experimentFeatures = epy.processing.features;
        experimentInfoResult.features = experimentFeatures.stream().map(e -> ExperimentService.asExperimentFeatureData(e, taskVertices, epy.processing.taskFeatures)).collect(Collectors.toList());
        experimentInfoResult.execContext = execContext;
        experimentInfoResult.execContextState = EnumsApi.ExecContextState.toState(execContext.getState());
        result.experiment = ExperimentService.asExperimentData(experiment);
        result.experimentInfo = experimentInfoResult;

        List<TaskProgress> taskProgresses = taskRepository.getTaskProgress(execContext.id);
        result.progress = taskProgresses.stream()
                .sorted(Comparator.comparingInt(t -> t.execState))
                .map(t->new ExperimentApiData.ExperimentProgressResult(t.count, t.execState, EnumsApi.TaskExecState.from(t.execState).toString(), t.isCompleted, t.isResultReceived))
                .collect(Collectors.toList());
        return result;
    }

    public ExperimentApiData.ExperimentsEditResult editExperiment(@PathVariable Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentsEditResult("#285.100 experiment wasn't found, experimentId: " + id);
        }
        Iterable<Function> functions = functionRepository.findAll();
        ExperimentApiData.FunctionResult functionResult = new ExperimentApiData.FunctionResult();

        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        final List<String> functionCodes = epy.getFunctionCodes();
        List<Function> experimentFunctions = functionService.getFunctionsForCodes(functionCodes);

        functionResult.functions = experimentFunctions.stream().map(es->
                new ExperimentApiData.ExperimentFunctionResult(
                        es.getId(), es.getVersion(), es.getCode(), es.type, experiment.id)).collect(Collectors.toList());

        functionResult.selectOptions = functionService.getSelectOptions(
                functions,
                functionResult.functions.stream().map(o -> new FunctionData.FunctionCode(o.getId(), o.getFunctionCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!experimentFunctionTypes.contains(s.type) ) {
                        return true;
                    }
                    if (CommonConsts.FIT_TYPE.equals(s.type) && functionService.hasType(experimentFunctions, CommonConsts.FIT_TYPE)) {
                        return true;
                    }
                    else if (CommonConsts.PREDICT_TYPE.equals(s.type) && functionService.hasType(experimentFunctions, CommonConsts.PREDICT_TYPE)) {
                        return true;
                    }
                    else if (CommonConsts.CHECK_FITTING_TYPE.equals(s.type)) {
                        if (functionService.hasType(experimentFunctions, CommonConsts.CHECK_FITTING_TYPE)) {
                            return true;
                        }
                        for (Function function : experimentFunctions) {
                            if (CommonConsts.PREDICT_TYPE.equals(function.getType())) {
                                final Meta meta = MetaUtils.getMeta(function.getFunctionConfig(false).metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
                                if (MetaUtils.isTrue(meta)) {
                                    // don't include this Function because 'predict' function doesn't support fitting detection
                                    return false;
                                }
                            }
                        }
                        return true;
                    } else {
                        return false;
                    }
                });

        functionResult.sortFunctionsByOrder();
        ExperimentApiData.ExperimentsEditResult result = new ExperimentApiData.ExperimentsEditResult();

        ExperimentApiData.HyperParamsResult r = new ExperimentApiData.HyperParamsResult();
        r.items = epy.experimentYaml.getHyperParams().stream().map(ExperimentTopLevelService::asHyperParamData).collect(Collectors.toList());
        result.hyperParams = r;
        result.simpleExperiment = asSimpleExperiment(experiment);
        result.functionResult = functionResult;
        return result;
    }

    private void addCheckFittingFunction(ExperimentParamsYaml epy, List<Function> experimentFunctions) {
        if (S.b(epy.experimentYaml.checkFittingFunction)) {
            return;
        }
        experimentFunctions.stream()
                .filter(s->s.type.equals(CommonConsts.PREDICT_TYPE))
                .findFirst()
                .ifPresent(s-> {
                    Meta m = MetaUtils.getMeta(s.getFunctionConfig(false).metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
                    if (MetaUtils.isTrue(m)) {
                        experimentFunctions.add(functionRepository.findByCode(epy.experimentYaml.checkFittingFunction));
                    }
        });
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

    public OperationStatusRest functionAddCommit(Long id, String functionCode) {
        Experiment experiment = experimentRepository.findByIdForUpdate(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.200 experiment wasn't found, id: "+id );
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        Function s = functionService.findByCode(functionCode);
        if (s==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.210 function wasn't found, id: "+id );

        }
        switch(s.getType()){
            case "fit":
                epy.experimentYaml.fitFunction = functionCode;
                break;
            case "predict":
                epy.experimentYaml.predictFunction = functionCode;
                break;
            case "check-fitting":
                epy.experimentYaml.checkFittingFunction = functionCode;
                break;
            default:
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.220 function has non-supported type for an experiment: "+s.getType() );
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

    public OperationStatusRest functionDeleteCommit(Long experimentId, String functionType) {
        if (functionType==null || functionType.isBlank()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.245 functionType is blank");
        }
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.250 An experiment wasn't found, id: "+experimentId );
        }

        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        if (Objects.equals(epy.experimentYaml.fitFunction, functionType)) {
            epy.experimentYaml.fitFunction = null;
        }
        else if (Objects.equals(epy.experimentYaml.predictFunction, functionType)) {
            epy.experimentYaml.predictFunction = null;
            epy.experimentYaml.checkFittingFunction = null;
        }
        else if (Objects.equals(epy.experimentYaml.checkFittingFunction, functionType)) {
            epy.experimentYaml.checkFittingFunction = null;
        }
        else {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.252 Can't find a function with the type: "+functionType );
        }
        experiment.updateParams(epy);
        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest functionDeleteByTypeCommit(Long experimentId, String functionType) {
        if (S.b(functionType)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.245 functionType is blank");
        }
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.250 An experiment wasn't found, id: "+experimentId );
        }

        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        switch(functionType) {
            case CommonConsts.FIT_TYPE:
                epy.experimentYaml.fitFunction = null;
                break;
            case CommonConsts.PREDICT_TYPE:
                epy.experimentYaml.predictFunction = null;
                epy.experimentYaml.checkFittingFunction = null;
                break;
            case CommonConsts.CHECK_FITTING_TYPE:
                epy.experimentYaml.checkFittingFunction = null;
                break;
            default:
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.254 Unknown type of function: "+functionType );
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
                    "#285.270 An experiment wasn't found, experimentId: " + id);
        }
        // do not use experiment.getExperimentParamsYaml() because it's  caching ExperimentParamsYaml
        ExperimentParamsYaml epy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experiment.getParams());
        epy.processing = new ExperimentParamsYaml.ExperimentProcessing();
        epy.createdOn = System.currentTimeMillis();

        final Experiment e = new Experiment();
        String newCode = StrUtils.incCopyNumber(experiment.getCode());
        int i = 0;
        while ( experimentRepository.findIdByCode(newCode)!=null  ) {
            newCode = StrUtils.incCopyNumber(newCode);
            if (i++>100) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.273 Can't find a new code for experiment with the code: " + experiment.getCode());
            }
        }
        epy.experimentYaml.code = newCode;
        e.code = newCode;
        e.updateParams(epy);
        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
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

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("mh-experiment-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.340 can't create temporary directory in " + location);
            }
            final File experimentFile = new File(tempDir, "experiment" + ext);
            log.debug("Start storing an uploaded experiment to disk");
            try(OutputStream os = new FileOutputStream(experimentFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start loading experiment into db");
            String yaml = FileUtils.readFileToString(experimentFile, StandardCharsets.UTF_8);
            OperationStatusRest result = addExperiment(yaml);

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages, result.infoMessages);
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("#285.350 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.360 can't load source codes, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
    }

    public OperationStatusRest addExperiment(String experimentYamlAsStr) {
        if (StringUtils.isBlank(experimentYamlAsStr)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.370 source code yaml is empty");
        }

        ExperimentParamsYaml ppy;
        try {
            ppy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experimentYamlAsStr);
        } catch (WrongVersionOfYamlFileException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.380 Error parsing yaml: " + e.getMessage());
        }

        final String code = ppy.experimentYaml.code;
        if (StringUtils.isBlank(code)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.390 code of experiment is empty");
        }
        ppy.createdOn = System.currentTimeMillis();

        Long experimentId = experimentRepository.findIdByCode(code);
        if (experimentId!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.400 The experiment with such code already exists, code: " + code);
        }

        Experiment e = new Experiment();
        e.code = ppy.experimentYaml.code;
        e.updateParams(ppy);

        experimentCache.save(e);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest toAtlas(Long id) {

        Experiment experiment = experimentCache.findById(id);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.410 can't find experiment for id: " + id);
        }

        if (experiment.execContextId ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.420 This experiment isn't bound to ExecContext");
        }
        execContextFSM.toExportingToAtlas(experiment.execContextId);
        return  new OperationStatusRest(EnumsApi.OperationStatus.OK,"Exporting of experiment was successfully started", null);
    }

    public OperationStatusRest bindExperimentToSourceCodeWithResource(String experimentCode, String resourcePoolCode, DispatcherContext context) {
        if (resourcePoolCode==null || resourcePoolCode.isBlank()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.480 resource pool code is blank");
        }
        if (experimentCode==null || experimentCode.isBlank()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.485 experiment code is blank");
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.500 can't find an experiment for code: " + experimentCode);
        }
        if (experiment.execContextId !=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.502 an experiment '"+experimentCode+"' was already bound to sourceCode");
        }
        if (true) {
            throw new NotImplementedException("Need to re-write logic of working with experiment and sourceCode");
        }
        SourceCodeImpl p = null;
/*
        p = getSourceCodeByExperimentCode(experimentCode);
        if (p==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.510 can't find a sourceCode with experiment code: " + experimentCode);
        }
*/
        SourceCodeApiData.ExecContextResult execContextResultRest = sourceCodeTopLevelService.addExecContext(p.id, resourcePoolCode, context);
        if (execContextResultRest.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, execContextResultRest.errorMessages, execContextResultRest.infoMessages);
        }

        experimentService.bindExperimentToExecContext(experiment.id, execContextResultRest.execContext.getId());

        return  new OperationStatusRest(EnumsApi.OperationStatus.OK,
                "Binding an experiment '"+experimentCode+"' to sourceCode '"+p.uid +"' with using a resource '"+resourcePoolCode+"' was successful", null);
    }



    public OperationStatusRest produceTasks(String experimentCode) {
        return changeExecStateTo(experimentCode, EnumsApi.ExecContextState.PRODUCING);
    }

    public EnumsApi.ExecContextState getExperimentProcessingStatus(String experimentCode) {
        if (experimentCode==null || experimentCode.isBlank()) {
            return EnumsApi.ExecContextState.UNKNOWN;
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null || experiment.execContextId ==null) {
            return EnumsApi.ExecContextState.UNKNOWN;
        }
        ExecContext wb = execContextCache.findById(experiment.execContextId);
        return EnumsApi.ExecContextState.toState(wb.getState());
    }

    public OperationStatusRest startProcessingOfTasks(String experimentCode) {
        return changeExecStateTo(experimentCode, EnumsApi.ExecContextState.STARTED);
    }

    private OperationStatusRest changeExecStateTo(String experimentCode, EnumsApi.ExecContextState execState) {
        if (experimentCode==null || experimentCode.isBlank()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.550 experiment code is blank");
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.560 can't find an experiment for code: " + experimentCode);
        }
        OperationStatusRest status = execContextService.execContextTargetState(experiment.execContextId, execState);
        if (status.isErrorMessages()) {
            return status;
        }
        return  new OperationStatusRest(EnumsApi.OperationStatus.OK,
                "State of experiment '"+experimentCode+"' was successfully changed to " + execState, null);
    }

}
