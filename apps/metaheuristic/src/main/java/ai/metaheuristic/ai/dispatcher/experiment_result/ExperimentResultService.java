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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.experiment.MetricsMaxValueCollector;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentTaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.ControllerUtils;
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
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

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
    private final ExecContextCache execContextCache;
    private final VariableService variableService;
    private final MetricsMaxValueCollector metricsMaxValueCollector;

    private static final String INLINE_KEY = "inline-key";
    private static final String PERMUTE_INLINE = "permute-inline";

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

    public ExperimentResultData.ExperimentResultSimpleList getExperimentResultExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentResultRowsLimit, pageable);
        ExperimentResultData.ExperimentResultSimpleList result = new ExperimentResultData.ExperimentResultSimpleList();
        result.items = experimentResultRepository.findAllAsSimple(pageable);
        return result;
    }

    public OperationStatusRest storeExperimentToExperimentResult(Long execContextId, TaskParamsYaml taskParamsYaml, ExecContextParamsYaml.VariableDeclaration variableDeclaration) {
        Long experimentId = experimentRepository.findIdByExecContextId(execContextId);

        if (experimentId==null ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#604.020 Can't find experiment for execContextId #" + execContextId);
        }

        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.040 can't find experiment for id: " + experimentId);
        }
        ExecContextImpl execContext = execContextCache.findById(experiment.execContextId);
        if (execContext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.060 can't find execContext for this experiment");
        }

        StoredToExperimentResultWithStatus stored = toExperimentStoredToExperimentResult(execContext, experiment);
        if (stored.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, stored.getErrorMessagesAsList());
        }
        if (!execContextId.equals(stored.experimentResultParamsYamlWithCache.experimentResult.execContext.execContextId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#604.100 Experiment can't be stored, execContextId is different");
        }

        String metricsVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "metrics");
        if (S.b(metricsVariableName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"Meta 'metrics' must be defined and can't be empty");
        }
        String featureVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "feature-item");
        if (S.b(featureVariableName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"Meta 'feature-item' must be defined and can't be empty");
        }

        InlineVariableData.InlineVariableItem item = InlineVariableUtils.getInlineVariableItem(variableDeclaration, taskParamsYaml.task.metas);
        if (S.b(item.inlineKey)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "Meta 'inline-key' wasn't found or empty.");
        }
        if (item.inlines == null || item.inlines.isEmpty()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "Inline variable '" + item.inlineKey + "' wasn't found or empty. List of keys in inlines: " + variableDeclaration.inline.keySet());
        }

        List<SimpleVariable> metricsVariables = variableService.getSimpleVariablesInExecContext(execContextId, metricsVariableName);
        List<SimpleVariable> featureVariables = variableService.getSimpleVariablesInExecContext(execContextId, featureVariableName);
        Set<String> taskContextIds = metricsVariables.stream().map(v->v.taskContextId).collect(Collectors.toSet());

        ExperimentResult a = new ExperimentResult();
        try {
            a.params = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.toString(stored.experimentResultParamsYamlWithCache.experimentResult);
        } catch (YAMLException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#604.140 General error while storing experiment, " + e.toString());
        }
        ExperimentResultParamsYaml erpy = stored.experimentResultParamsYamlWithCache.experimentResult;

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

        List<Long> ids = taskRepository.findAllTaskIdsByExecContextId(execContext.getId());
        AtomicLong featureId = new AtomicLong(1);
        AtomicLong taskFeatureId = new AtomicLong(1);
        final List<ExperimentFeature> features = new ArrayList<>();
        final List<ExperimentTaskFeature> taskFeatures = new ArrayList<>();

        // store all tasks' results which are the tasks for fitting/predicting only
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

            List<SimpleVariable> metricsVs = metricsVariables.stream().filter(v->v.taskContextId.equals(taskContextId)).collect(Collectors.toList());
            if (metricsVs.isEmpty()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        S.f("Variable '%s' with taskContext '%s' wasn't found in execContext #%d", metricsVariableName, taskContextId, execContextId));
            }
            if (metricsVs.size()>1) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        S.f("Too many variables '%s' with taskContext '%s' in execContext #%d, actual count is ",
                                metricsVariableName, taskContextId, execContextId, metricsVs.size()));
            }
            List<SimpleVariable> featureVs = featureVariables.stream().filter(v->v.taskContextId.equals(taskContextId)).collect(Collectors.toList());
            if (featureVs.isEmpty()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        S.f("Variable '%s' with taskContext '%s' wasn't found in execContext #%d", featureVariableName, taskContextId, execContextId));
            }
            if (featureVs.size()>1) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        S.f("Too many variables '%s' with taskContext '%s' in execContext #%d, actual count is ",
                                featureVariableName, taskContextId, execContextId, featureVs.size()));
            }
            VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(variableService.getVariableDataAsString(featureVs.get(0).id));

            String metrics = variableService.getVariableDataAsString(metricsVs.get(0).id);
            MetricValues mvs = MetricsUtils.getMetricValues(metrics);
            Double maxValue = 0.0;

            ExperimentFeature feature = new ExperimentFeature(
                    featureId.getAndIncrement(), vapy.array.stream().map(v -> v.realName).collect(Collectors.toList()),
                    EnumsApi.ExecContextState.FINISHED.code, experimentId, maxValue);
            features.add(feature);
            taskFeatures.add(new ExperimentTaskFeature(
                    taskFeatureId.getAndIncrement(), execContextId, t.id, feature.id, EnumsApi.ExperimentTaskType.UNKNOWN.value
            ));

            ExperimentResultTaskParamsYaml atpy = new ExperimentResultTaskParamsYaml();
            atpy.assignedOn = t.getAssignedOn();
            atpy.completed = t.isCompleted();
            atpy.completedOn = t.getCompletedOn();
            atpy.execState = t.getExecState();
            atpy.taskId = t.getId();
            atpy.taskParams = t.getParams();
            // typeAsString will have been initialized when ExperimentResultTaskParamsYaml will be requested
            // see method ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultTopLevelService.findTasks
            atpy.typeAsString = null;
            atpy.functionExecResults = t.getFunctionExecResults();
            atpy.metrics = metrics;

            ExperimentTask at = new ExperimentTask();
            at.experimentResultId = experimentResult.id;
            at.taskId = t.getId();
            at.params = ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.toString(atpy);
            experimentTaskRepository.save(at);
        }

        updateData(experimentResult, stored.experimentResultParamsYamlWithCache.experimentResult, item, features, taskFeatures);
        return OperationStatusRest.OPERATION_STATUS_OK;
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

        item.inlines.entrySet().stream().map(e->new ExperimentApiData.HyperParam()).collect(Collectors.toCollection(()->experimentResultParamsYaml.hyperParams));
        experimentResultParamsYaml.features.addAll(features);
        experimentResultParamsYaml.taskFeatures.addAll(taskFeatures);

        experimentResult.params = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.toString(experimentResultParamsYaml);
        experimentResultRepository.save(experimentResult);
    }

    public void updateMaxValueForExperimentFeatures(Long experimentId) {
        ExperimentResult er = experimentResultRepository.findById(experimentId).orElse(null);
        if (er==null) {
            return;
        }
        ExperimentResultParamsYaml erpy = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.to(er.params);

        List<ExperimentFeature> features = erpy.features;
        log.info("Start calculatingMaxValueOfMetrics");
        for (ExperimentFeature feature : features) {
            double value = metricsMaxValueCollector.calcMaxValueForMetrics(erpy, feature.getId());
            log.info("\tFeature #{}, max value: {}", feature.getId(), value);
            feature.setMaxValue(value);
        }
        erpy.maxValueCalculated = true;
        er.params = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.toString(erpy);
        experimentResultRepository.save(er);
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
