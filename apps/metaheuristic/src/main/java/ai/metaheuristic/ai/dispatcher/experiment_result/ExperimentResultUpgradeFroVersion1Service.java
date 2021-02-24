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

import ai.metaheuristic.ai.dispatcher.beans.ExperimentResult;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentTaskRepository;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlUtilsV1;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultTaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYamlV1;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 2/23/2021
 * Time: 11:38 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentResultUpgradeFroVersion1Service {

    private final ExperimentResultRepository experimentResultRepository;
    private final ExperimentTaskRepository experimentTaskRepository;
    private final ExecContextGraphService execContextGraphService;

    @Transactional
    public String upgrade(Long experimentResultId) {
        ExperimentResult er = experimentResultRepository.findById(experimentResultId).orElse(null);
        if (er==null) {
            throw new RuntimeException("#458.020 can't find experimentResult #"+ experimentResultId);
        }

        final ExperimentResultParamsYamlUtilsV1 forVersion = (ExperimentResultParamsYamlUtilsV1) ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
        if (forVersion==null) {
            throw new IllegalStateException("(forVersion==null)");
        }
        ExperimentResultParamsYamlV1 v1 = forVersion.to(er.params);
        ExperimentResultParamsYaml v = upgradeTo(v1);

        ExecContextParamsYaml ecpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(v.execContext.execContextParams);

        final Set<ExecContextData.TaskVertex> vertices = ExecContextGraphService.importProcessGraph(ecpy).vertexSet();
        Set<String> taskContextIds = vertices.stream()
                .map(o->o.taskContextId)
                .filter(o->o.contains(ContextUtils.CONTEXT_SEPARATOR))
                .collect(Collectors.toSet());

        for (String taskContextId : taskContextIds) {
            ExperimentResultParamsYaml.ExperimentPart part = new ExperimentResultParamsYaml.ExperimentPart();
            part.taskContextId = taskContextId;

            final ExperimentResultTaskParamsYaml task = vertices.stream()
                    .filter(o -> o.taskContextId.equals(taskContextId))
                    .map(o -> experimentTaskRepository.findByExperimentResultIdAndTaskId(experimentResultId, o.taskId))
                    .filter(Objects::nonNull)
                    .map(o -> ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params))
                    .findFirst().orElse(null);

            if (task==null) {
                throw new RuntimeException("#458.020 can't find any experimentTaskResult in taskContextId #"+taskContextId);
            }

            part.fitting = task.fitting;
            part.hyperParams = task.taskParams.inline;
            part.metrics.values.putAll(task.metrics.values);

            v.parts.add(part);
        }

        er.params = ExperimentResultParamsYamlUtils.BASE_YAML_UTILS.toString(v);
        experimentResultRepository.save(er);

        return er.params;
    }

    public ExperimentResultParamsYaml upgradeTo(@NonNull ExperimentResultParamsYamlV1 src) {
        src.checkIntegrity();
        ExperimentResultParamsYaml trg = new ExperimentResultParamsYaml();
        trg.createdOn = src.createdOn;
        trg.code = src.code;
        trg.name = src.name;
        trg.description = src.description;
        trg.maxValueCalculated = src.maxValueCalculated;
        trg.numberOfTask = src.numberOfTask;

        trg.execContext = new ExperimentResultParamsYaml.ExecContextWithParams(src.execContext.execContextId, src.execContext.execContextParams);
        trg.hyperParams.addAll(src.hyperParams);
        src.features.stream().map(ExperimentResultUpgradeFroVersion1Service::toFeature).collect(Collectors.toCollection(()->trg.features));
        src.taskFeatures.stream().map(ExperimentResultUpgradeFroVersion1Service::toTaskFeature).collect(Collectors.toCollection(()->trg.taskFeatures));

        trg.checkIntegrity();
        return trg;
    }

    private static ExperimentResultParamsYaml.ExperimentTaskFeature toTaskFeature(ExperimentResultParamsYamlV1.ExperimentTaskFeatureV1 src) {
        ExperimentResultParamsYaml.ExperimentTaskFeature etf = new ExperimentResultParamsYaml.ExperimentTaskFeature();

        etf.id = src.id;
        etf.execContextId = src.execContextId;
        etf.taskId = src.taskId;
        etf.featureId = src.featureId;
        etf.taskType = src.taskType;
        etf.metrics = new ExperimentResultParamsYaml.MetricValues(src.metrics.values);

        return etf;
    }

    private static ExperimentResultParamsYaml.ExperimentFeature toFeature(ExperimentResultParamsYamlV1.ExperimentFeatureV1 src) {
        ExperimentResultParamsYaml.ExperimentFeature ef = new ExperimentResultParamsYaml.ExperimentFeature();
        ef.id = src.id;
        ef.variables = src.variables;
        ef.execStatus = src.execStatus;
        ef.experimentId = src.experimentId;
        ef.maxValues.putAll(src.maxValues);

        return ef;
    }

}
