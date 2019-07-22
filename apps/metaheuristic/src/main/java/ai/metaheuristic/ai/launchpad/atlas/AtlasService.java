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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.Atlas;
import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.data.AtlasData;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentCache;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.AtlasRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlWithCache;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasService {

    private final Globals globals;
    private final PlanCache planCache;
    private final ExperimentCache experimentCache;
    private final TaskRepository taskRepository;
    private final AtlasRepository atlasRepository;
    private final WorkbookCache workbookCache;

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class StoredToAtlasWithStatus extends BaseDataClass {
        public AtlasParamsYamlWithCache atlasParamsYamlWithCache;
        public Enums.StoringStatus status;

        public StoredToAtlasWithStatus(Enums.StoringStatus status, String errorMessage) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }
    }

    public AtlasData.AtlasSimpleExperiments getAtlasExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.atlasExperimentRowsLimit, pageable);
        AtlasData.AtlasSimpleExperiments result = new AtlasData.AtlasSimpleExperiments();
        result.items = atlasRepository.findAllAsSimple(pageable);
        return result;
    }

    public OperationStatusRest toAtlas(Long workbookId, long experimentId) {
        StoredToAtlasWithStatus stored = toExperimentStoredToAtlas(experimentId);
        if (stored.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, stored.errorMessages);
        }
        if (!workbookId.equals(stored.atlasParamsYamlWithCache.atlasParams.workbook.workbookId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Experiment can't be stored, workbookId is different");
        }
        // TODO 2019-07-13 need to re-write this check
/*
        String poolCode = getPoolCodeForExperiment(workbookId, experimentId);
        List<SimpleCodeAndStorageUrl> codes = binaryDataService.getResourceCodesInPool(List.of(poolCode), workbookId);
        if (!codes.isEmpty()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Experiment already stored");
        }
*/
        Atlas b = new Atlas();
        try {
            b.params = AtlasParamsYamlUtils.BASE_YAML_UTILS.toString(stored.atlasParamsYamlWithCache.atlasParams);
        } catch (YAMLException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "General error while storing experiment, " + e.toString());
        }
        ExperimentParamsYaml epy = stored.atlasParamsYamlWithCache.getExperimentParamsYaml();

        b.name = epy.experimentYaml.name;
        b.description = epy.experimentYaml.description;
        b.code = epy.experimentYaml.code;
        b.createdOn = System.currentTimeMillis();
        atlasRepository.save(b);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public static String getPoolCodeForExperiment(long workbookId, long experimentId) {
        return String.format("stored-experiment-%d-%d",workbookId, experimentId);
    }

    public StoredToAtlasWithStatus toExperimentStoredToAtlas(long experimentId) {

        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment==null) {
            return new StoredToAtlasWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.02 can't find experiment for id: " + experimentId);
        }
        WorkbookImpl workbook = workbookCache.findById(experiment.workbookId);
        if (workbook==null) {
            return new StoredToAtlasWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.05 can't find workbook for this experiment");
        }
        PlanImpl plan = planCache.findById(workbook.getPlanId());
        if (plan==null) {
            return new StoredToAtlasWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.10 can't find plan for this experiment");
        }

        AtlasParamsYaml atlasParamsYaml = new AtlasParamsYaml();
        atlasParamsYaml.createdOn = System.currentTimeMillis();
        atlasParamsYaml.plan = new AtlasParamsYaml.PlanWithParams(plan.id, plan.params);
        atlasParamsYaml.workbook = new AtlasParamsYaml.WorkbookWithParams(workbook.id, workbook.params, workbook.execState);
        atlasParamsYaml.experiment = new AtlasParamsYaml.ExperimentWithParams(experiment.id, experiment.params);
        atlasParamsYaml.tasks = taskRepository.findAllByWorkbookId(workbook.getId()).stream()
                .map(o-> {
                    String typeAsString = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams()).taskYaml.snippet.type;
                    return new AtlasParamsYaml.TaskWithParams(
                            o.getId(), o.getParams(), o.getExecState(), o.getMetrics(), o.getSnippetExecResults(),
                            o.getCompletedOn(),
                            o.isCompleted(),
                            o.getAssignedOn(),
                            typeAsString

                    );
                })
                .collect(Collectors.toList());

        StoredToAtlasWithStatus result = new StoredToAtlasWithStatus();
        result.atlasParamsYamlWithCache = new AtlasParamsYamlWithCache( atlasParamsYaml );
        result.status = Enums.StoringStatus.OK;
        return result;
    }

/*
    public ConsoleOutputStoredToAtlas toConsoleOutputStoredToAtlas(long workbookId) {
        //noinspection UnnecessaryLocalVariable
        ConsoleOutputStoredToAtlas result = consoleFormAtlasService.collectConsoleOutputs(workbookId);
        return result;
    }
*/
}
