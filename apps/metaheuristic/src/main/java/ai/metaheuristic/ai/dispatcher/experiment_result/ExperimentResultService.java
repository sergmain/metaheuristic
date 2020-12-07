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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.ai.dispatcher.data.InlineVariableData;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentTaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlWithCache;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultTaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYaml;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYaml.*;

@SuppressWarnings("DuplicatedCode")
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentResultService {

    private final Globals globals;
    private final ExperimentCache experimentCache;
    private final ExperimentRepository experimentRepository;
    private final TaskRepository taskRepository;
    private final ExperimentResultRepository experimentResultRepository;
    private final ExperimentTaskRepository experimentTaskRepository;
    private final VariableService variableService;

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class StoredToExperimentResultWithStatus extends BaseDataClass {
        public ExperimentResultParamsYamlWithCache experimentResultParamsYamlWithCache;
        public Enums.StoringStatus status;

        public StoredToExperimentResultWithStatus(Enums.StoringStatus status, String errorMessage) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }
    }

    @Data
    @AllArgsConstructor
    public static class VariableWithStatus {
        public SimpleVariable variable;
        public OperationStatusRest status;

        public VariableWithStatus(OperationStatusRest status) {
            this.status = status;
        }
    }

    public ExperimentResultData.ExperimentResultSimpleList getExperimentResultExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentResultRowsLimit, pageable);
        ExperimentResultData.ExperimentResultSimpleList result = new ExperimentResultData.ExperimentResultSimpleList();
        result.items = experimentResultRepository.findAllAsSimple(pageable);
        return result;
    }

    public OperationStatusRest storeExperimentToExperimentResult(ExecContextImpl execContext, TaskParamsYaml taskParamsYaml) {
        Long experimentId = experimentRepository.findIdByExecContextId(execContext.id);

        if (experimentId==null ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#604.020 Can't find experiment for execContextId #" + execContext.id);
        }

        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.040 can't find experiment for id: " + experimentId);
        }

        StoredToExperimentResultWithStatus stored = toExperimentStoredToExperimentResult(execContext, experiment);
        if (stored.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, stored.getErrorMessagesAsList());
        }
        if (!execContext.id.equals(stored.experimentResultParamsYamlWithCache.experimentResult.execContext.execContextId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#604.100 Experiment can't be stored, execContextId is different");
        }

        String metricsVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "metrics");
        if (S.b(metricsVariableName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.120 Meta 'metrics' must be defined and can't be empty");
        }
        String featureVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "feature-item");
        if (S.b(featureVariableName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.140 Meta 'feature-item' must be defined and can't be empty");
        }
        String fittingVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "fitting");
        if (S.b(fittingVariableName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.145 Meta 'fitting' must be defined and can't be empty");
        }
        String inlineVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "inline-permutation");
        if (S.b(inlineVariableName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.147 Meta 'inline-permutation' must be defined and can't be empty");
        }

        ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        ExecContextParamsYaml.VariableDeclaration variableDeclaration = execContextParamsYaml.variables;

        InlineVariableData.InlineVariableItem inlineVariableItem = InlineVariableUtils.getInlineVariableItem(variableDeclaration, taskParamsYaml.task.metas);
        if (S.b(inlineVariableItem.inlineKey)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#604.160 Meta 'inline-key' wasn't found or empty.");
        }
        if (inlineVariableItem.inlines == null || inlineVariableItem.inlines.isEmpty()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#604.180 Inline variable '" + inlineVariableItem.inlineKey + "' wasn't found or empty. List of keys in inlines: " + variableDeclaration.inline.keySet());
        }

        List<SimpleVariable> metricsVariables = variableService.getSimpleVariablesInExecContext(execContext.id, metricsVariableName);
        List<SimpleVariable> featureVariables = variableService.getSimpleVariablesInExecContext(execContext.id, featureVariableName);
        List<SimpleVariable> fittingVariables = variableService.getSimpleVariablesInExecContext(execContext.id, fittingVariableName);
        List<SimpleVariable> inlineVariables = variableService.getSimpleVariablesInExecContext(execContext.id, inlineVariableName);
        Set<String> taskContextIds = metricsVariables.stream().map(v->v.taskContextId).collect(Collectors.toSet());
        featureVariables.stream().map(v->v.taskContextId).collect(Collectors.toCollection(()->taskContextIds));
        fittingVariables.stream().map(v->v.taskContextId).collect(Collectors.toCollection(()->taskContextIds));
        inlineVariables.stream().map(v->v.taskContextId).collect(Collectors.toCollection(()->taskContextIds));

        List<Long> ids = taskRepository.findAllTaskIdsByExecContextId(execContext.getId());

        ExperimentResultParamsYaml erpy = stored.experimentResultParamsYamlWithCache.experimentResult;
        erpy.numberOfTask = ids.size();

        ExperimentResult a = new ExperimentResult();
        try {
            a.params = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.toString(erpy);
        } catch (YAMLException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#604.200 General error while storing experiment, " + e.toString());
        }

        a.name = erpy.name;
        a.description = erpy.description;
        a.code = erpy.code;
        a.createdOn = System.currentTimeMillis();
        a.companyId = execContext.companyId;
        final ExperimentResult experimentResult = experimentResultRepository.save(a);

/*
        ExperimentParamsYaml epy = e.getExperimentParamsYaml();
        if (!epy.processing.maxValueCalculated) {
            updateMaxValueForExperimentFeatures(e.id);
        }
*/

        AtomicLong featureId = new AtomicLong(1);
        AtomicLong taskFeatureId = new AtomicLong(1);
        final List<ExperimentFeature> features = new ArrayList<>();
        final List<ExperimentTaskFeature> taskFeatures = new ArrayList<>();

        for (Long taskId : ids) {

            TaskImpl t = taskRepository.findById(taskId).orElse(null);
            if (t == null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"Task #"+taskId+" wasn't found");
            }
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(t.getParams());
            String taskContextId = tpy.task.taskContextId;
            if (!taskContextIds.contains(taskContextId)) {
                log.info(S.f("Skip task %s with taskContextId #%s", t.id, taskContextId));
                continue;
            }
            if (CollectionUtils.isEmpty(tpy.task.inline)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#604.210 Task inline wasn't found or empty.");
            }
            VariableWithStatus metricsVar = getVariableWithStatus(metricsVariables, execContext.id, taskContextId, metricsVariableName);
            if (metricsVar.status.status!= EnumsApi.OperationStatus.OK) {
                return metricsVar.status;
            }
            VariableWithStatus featureVar = getVariableWithStatus(featureVariables, execContext.id, taskContextId, featureVariableName);
            if (featureVar.status.status!= EnumsApi.OperationStatus.OK) {
                return metricsVar.status;
            }
            VariableWithStatus fittingVar = getVariableWithStatus(fittingVariables, execContext.id, taskContextId, fittingVariableName);
            if (fittingVar.status.status!= EnumsApi.OperationStatus.OK) {
                return metricsVar.status;
            }
            VariableWithStatus inlineVar = getVariableWithStatus(inlineVariables, execContext.id, taskContextId, inlineVariableName);
            if (inlineVar.status.status!= EnumsApi.OperationStatus.OK) {
                return metricsVar.status;
            }

            VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(variableService.getVariableDataAsString(featureVar.variable.id));

            String metrics = variableService.getVariableDataAsString(metricsVar.variable.id);
            MetricValues mvs = MetricsUtils.getMetricValues(metrics);

            ExperimentFeature feature = findOrCreate(features, experimentId, featureId, vapy);
            taskFeatures.add(new ExperimentTaskFeature(
                    taskFeatureId.getAndIncrement(), execContext.id, t.id, feature.id, EnumsApi.ExperimentTaskType.UNKNOWN.value,
                    new ExperimentResultParamsYaml.MetricValues(mvs.values)
            ));

            ExperimentResultTaskParamsYaml ertpy = new ExperimentResultTaskParamsYaml();
            ertpy.assignedOn = t.getAssignedOn();
            ertpy.completed = t.isCompleted();
            ertpy.completedOn = t.getCompletedOn();
            ertpy.execState = t.getExecState();
            ertpy.taskId = t.getId();

            String inlineAsStr = variableService.getVariableDataAsString(inlineVar.variable.id);
            Yaml yampUtil = YamlUtils.init(Map.class);
            Map<String, String> currInline = yampUtil.load(inlineAsStr);
            ertpy.taskParams = new ExperimentResultTaskParamsYaml.TaskParams(inlineVariableItem.inlines, currInline);

            // typeAsString will have been initialized when ExperimentResultTaskParamsYaml will be requested
            // see method ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultTopLevelService.findTasks
            ertpy.typeAsString = null;
            ertpy.functionExecResults = t.getFunctionExecResults();
            ertpy.metrics.values.putAll(mvs.values);
            ertpy.metrics.status = EnumsApi.MetricsStatus.Ok;

            String fitting = variableService.getVariableDataAsString(fittingVar.variable.id);
            FittingYaml fittingYaml = FittingYamlUtils.BASE_YAML_UTILS.to(fitting);
            ertpy.fitting = fittingYaml.fitting;

            ExperimentTask at = new ExperimentTask();
            at.experimentResultId = experimentResult.id;
            at.taskId = t.getId();
            at.params = ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.toString(ertpy);
            experimentTaskRepository.save(at);
        }

        updateData(experimentResult, stored.experimentResultParamsYamlWithCache.experimentResult, inlineVariableItem, features, taskFeatures);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private static VariableWithStatus getVariableWithStatus(List<SimpleVariable> allVars, Long execContextId, String taskContextId, String varName) {
        List<SimpleVariable> variables = allVars.stream().filter(v->v.taskContextId.equals(taskContextId)).collect(Collectors.toList());
        if (variables.isEmpty()) {

            return new VariableWithStatus(new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    S.f("#604.220 Variable '%s' with taskContext '%s' wasn't found in execContext #%d", varName, taskContextId, execContextId)));
        }

        if (variables.size()>1) {
            return new VariableWithStatus( new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    S.f("#604.240 Too many variables '%s' with taskContext '%s' in execContext #%d, actual count is ",
                            varName, taskContextId, execContextId, variables.size())));
        }
        return new VariableWithStatus(variables.get(0), OperationStatusRest.OPERATION_STATUS_OK);
    }

    public static ExperimentFeature findOrCreate(List<ExperimentFeature> features, Long experimentId, AtomicLong featureId, VariableArrayParamsYaml vapy) {
        List<String> variables = vapy.array.stream().map(v -> v.name).collect(Collectors.toList());
        for (ExperimentFeature feature : features) {
            if (CollectionUtils.isEquals(feature.variables, variables)) {
                return feature;
            }
        }
        // actual value of maxValue will be calculated later
        ExperimentFeature experimentFeature = new ExperimentFeature(
                featureId.getAndIncrement(), variables, EnumsApi.ExecContextState.FINISHED.code, experimentId);

        features.add(experimentFeature);
        return experimentFeature;
    }


//    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(Experiment experiment, boolean isFull) {
//        return getHyperParamsAsMap(experiment.getExperimentParamsYaml().experimentYaml.hyperParams, isFull);
//    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(List<ExperimentApiData.HyperParam> hyperParams) {
        return getHyperParamsAsMap(hyperParams, true);
    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(List<ExperimentApiData.HyperParam> hyperParams, boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentApiData.HyperParam hyperParam : hyperParams) {
            InlineVariableUtils.NumberOfVariants ofVariants = InlineVariableUtils.getNumberOfVariants(hyperParam.getValues() );
            Map<String, Integer> map = new LinkedHashMap<>();
            paramByIndex.put(hyperParam.getKey(), map);
            for (int i = 0; i <ofVariants.values.size(); i++) {
                String value = ofVariants.values.get(i);


                map.put(isFull ? hyperParam.getKey()+'-'+value : value , i);
            }
        }
        return paramByIndex;
    }

    private void updateData(ExperimentResult experimentResult, ExperimentResultParamsYaml experimentResultParamsYaml,
                            InlineVariableData.InlineVariableItem item, List<ExperimentFeature> features, List<ExperimentTaskFeature> taskFeatures) {

        item.inlines.entrySet().stream()
                .map(e-> new ExperimentApiData.HyperParam(e.getKey(), e.getValue(), InlineVariableUtils.getNumberOfVariants(e.getValue()).count))
                .collect(Collectors.toCollection(()->experimentResultParamsYaml.hyperParams));
        experimentResultParamsYaml.features.addAll(features);
        experimentResultParamsYaml.taskFeatures.addAll(taskFeatures);

        updateMaxValueForExperimentResult(experimentResultParamsYaml);

        experimentResult.params = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.toString(experimentResultParamsYaml);
        experimentResultRepository.save(experimentResult);
    }

    private static void updateMaxValueForExperimentResult(ExperimentResultParamsYaml erpy) {
        log.info("Start calculatingMaxValueOfMetrics");
        for (ExperimentFeature feature : erpy.features) {
            erpy.taskFeatures.stream().filter(o->o.featureId.equals(feature.id)).forEach(o-> updateMax(feature, o.metrics));
        }
        erpy.maxValueCalculated = true;
    }

    private static void updateMax(ExperimentFeature feature, ExperimentResultParamsYaml.MetricValues metrics) {
        for (Map.Entry<String, BigDecimal> entry : metrics.values.entrySet()) {
            feature.maxValues.put(entry.getKey(),
                    Math.max(feature.maxValues.computeIfAbsent(entry.getKey(), o -> 0.0), entry.getValue().doubleValue()));
        }
    }

    public StoredToExperimentResultWithStatus toExperimentStoredToExperimentResult(ExecContextImpl execContext, Experiment experiment) {
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        ExperimentResultParamsYaml erpy = new ExperimentResultParamsYaml();
        erpy.createdOn = System.currentTimeMillis();
        erpy.execContext = new ExecContextWithParams(execContext.id, execContext.getParams());
        erpy.code = experiment.code;
        erpy.name = epy.code;
        erpy.description = epy.description;
        erpy.createdOn = epy.createdOn;

        StoredToExperimentResultWithStatus result = new StoredToExperimentResultWithStatus();
        result.experimentResultParamsYamlWithCache = new ExperimentResultParamsYamlWithCache( erpy );
        result.status = Enums.StoringStatus.OK;
        return result;
    }
}
