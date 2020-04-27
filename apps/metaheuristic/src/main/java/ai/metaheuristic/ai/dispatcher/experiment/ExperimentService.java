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
package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.BaseMetricElement;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.*;

@SuppressWarnings("DuplicatedCode")
@Service
@EnableTransactionManagement
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentService {

    private final ExecContextCache execContextCache;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;

    @Async
    @EventListener
    public void handleAsync(DispatcherInternalEvent.DeleteExecContextEvent event) {
        resetExperimentByExecContextId(event.execContextId);
    }

    public static int compareMetricElement(BaseMetricElement o2, BaseMetricElement o1) {
        for (int i = 0; i < Math.min(o1.getValues().size(), o2.getValues().size()); i++) {
            final BigDecimal holder1 = o1.getValues().get(i);
            if (holder1 == null) {
                return -1;
            }
            final BigDecimal holder2 = o2.getValues().get(i);
            if (holder2 == null) {
                return -1;
            }
            int c = ObjectUtils.compare(holder1, holder2);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(o1.getValues().size(), o2.getValues().size());
    }

    public static ExperimentApiData.ExperimentData asExperimentData(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();

        ExperimentApiData.ExperimentData ed = new ExperimentApiData.ExperimentData();
        ed.id = e.id;
        ed.code = e.code;
        ed.execContextId = e.execContextId;
        ed.name = params.name;
        ed.description = params.description;
        ed.hyperParams.addAll(params.processing.hyperParams);
        ed.hyperParamsAsMap.putAll(getHyperParamsAsMap(ed.hyperParams));
        ed.createdOn = params.createdOn;
        ed.numberOfTask = params.processing.numberOfTask;

        return ed;
    }

    @Nullable
    public ExperimentApiData.ExperimentData asExperimentDataShort(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        ExecContextImpl ec = execContextCache.findById(e.execContextId);
        if (ec==null) {
            log.warn("ExecContext wasn't found for id #"+e.execContextId);
            return null;
        }

        ExperimentApiData.ExperimentData ed = new ExperimentApiData.ExperimentData();
        ed.id = e.id;
        ed.state = ec.state;
        ed.version = e.version;
        ed.code = e.code;
        ed.execContextId = e.execContextId;
        ed.name = params.name;
        ed.description = params.description;
        ed.createdOn = params.createdOn;
        ed.numberOfTask = params.processing.numberOfTask;

        return ed;
    }

    public static ExperimentApiData.ExperimentFeatureData asExperimentFeatureData(
            @Nullable ExperimentFeature experimentFeature,
            List<ExecContextData.TaskVertex> taskVertices,
            List<ExperimentTaskFeature> taskFeatures) {

        final ExperimentApiData.ExperimentFeatureData featureData = new ExperimentApiData.ExperimentFeatureData();

        if (experimentFeature==null) {
            featureData.execStatus = Enums.FeatureExecStatus.finished_with_errors.code;
            featureData.execStatusAsString = Enums.FeatureExecStatus.finished_with_errors.info;
            return featureData;
        }

        BeanUtils.copyProperties(experimentFeature, featureData);

        List<ExperimentTaskFeature> etfs = taskFeatures.stream().filter(tf->tf.featureId.equals(featureData.id)).collect(Collectors.toList());

        Set<EnumsApi.TaskExecState> statuses = taskVertices
                .stream()
                .filter(t -> etfs
                        .stream()
                        .filter(etf-> etf.taskId.equals(t.taskId))
                        .findFirst()
                        .orElse(null) !=null ).map(o->o.execState)
                .collect(Collectors.toSet());

        Enums.FeatureExecStatus execStatus = statuses.isEmpty() ? Enums.FeatureExecStatus.empty : Enums.FeatureExecStatus.unknown;
        if (statuses.contains(EnumsApi.TaskExecState.OK)) {
            execStatus = Enums.FeatureExecStatus.finished;
        }
        if (statuses.contains(EnumsApi.TaskExecState.ERROR)|| statuses.contains(EnumsApi.TaskExecState.BROKEN)) {
            execStatus = Enums.FeatureExecStatus.finished_with_errors;
        }
        if (statuses.contains(EnumsApi.TaskExecState.NONE) || statuses.contains(EnumsApi.TaskExecState.IN_PROGRESS)) {
            execStatus = Enums.FeatureExecStatus.processing;
        }
        featureData.execStatusAsString = execStatus.info;
        return featureData;
    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(ExperimentParamsYaml epy) {
        return getHyperParamsAsMap(epy.processing.hyperParams, true);
    }

//    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(Experiment experiment, boolean isFull) {
//        return getHyperParamsAsMap(experiment.getExperimentParamsYaml().experimentYaml.hyperParams, isFull);
//    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(List<ExperimentApiData.HyperParam> experimentHyperParams) {
        return getHyperParamsAsMap(experimentHyperParams, true);
    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(List<ExperimentApiData.HyperParam> experimentHyperParams, boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentApiData.HyperParam hyperParam : experimentHyperParams) {
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

    private void resetExperimentByExecContextId(Long execContextId) {
        Experiment e = experimentRepository.findByExecContextIdForUpdate(execContextId);
        if (e==null) {
            return;
        }

        ExperimentParamsYaml epy = e.getExperimentParamsYaml();
        epy.processing = new ExperimentProcessing();
        e.updateParams(epy);
        e.setExecContextId(null);

        //noinspection UnusedAssignment
        e = experimentCache.save(e);
    }
}
