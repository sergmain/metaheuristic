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

package ai.metaheuristic.ai.mh.dispatcher..atlas;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mh.dispatcher..beans.*;
import ai.metaheuristic.ai.mh.dispatcher..data.AtlasData;
import ai.metaheuristic.ai.mh.dispatcher..experiment.ExperimentCache;
import ai.metaheuristic.ai.mh.dispatcher..source_code.SourceCodeCache;
import ai.metaheuristic.ai.mh.dispatcher..repositories.AtlasRepository;
import ai.metaheuristic.ai.mh.dispatcher..repositories.AtlasTaskRepository;
import ai.metaheuristic.ai.mh.dispatcher..repositories.ExperimentRepository;
import ai.metaheuristic.ai.mh.dispatcher..repositories.TaskRepository;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextCache;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextFSM;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlWithCache;
import ai.metaheuristic.ai.yaml.atlas.AtlasTaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.atlas.AtlasTaskParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.mh.dispatcher..Task;
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

@Slf4j
@Service
@Profile("mh.dispatcher.")
@RequiredArgsConstructor
public class AtlasService {

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final ExperimentCache experimentCache;
    private final ExperimentRepository experimentRepository;
    private final TaskRepository taskRepository;
    private final AtlasRepository atlasRepository;
    private final AtlasTaskRepository atlasTaskRepository;
    private final AtlasParamsYamlUtils atlasParamsYamlUtils;
    private final ExecContextCache execContextCache;
    private final ExecContextService execContextService;
    private final ExecContextFSM execContextFSM;

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

    public OperationStatusRest storeExperimentToAtlas(Long execContextId) {
        execContextFSM.toExportingToAtlasStarted(execContextId);
        Long experimentId = experimentRepository.findIdByExecContextId(execContextId);

        if (experimentId==null ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Can't find experiment for execContextId #" + execContextId);
        }

        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.02 can't find experiment for id: " + experimentId);
        }
        ExecContextImpl execContext = execContextCache.findById(experiment.execContextId);
        if (execContext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.05 can't find execContext for this experiment");
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#604.10 can't find sourceCode for this experiment");
        }

        StoredToAtlasWithStatus stored = toExperimentStoredToAtlas(sourceCode, execContext, experiment);
        if (stored.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, stored.errorMessages);
        }
        if (!execContextId.equals(stored.atlasParamsYamlWithCache.atlasParams.execContext.execContextId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Experiment can't be stored, execContextId is different");
        }
        // TODO 2019-07-13 need to re-write this check
/*
        String poolCode = getPoolCodeForExperiment(execContextId, experimentId);
        List<SimpleVariableAndStorageUrl> codes = binaryDataService.getResourceCodesInPool(List.of(poolCode), execContextId);
        if (!codes.isEmpty()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Experiment already stored");
        }
*/
        Atlas a = new Atlas();
        try {
            a.params = atlasParamsYamlUtils.BASE_YAML_UTILS.toString(stored.atlasParamsYamlWithCache.atlasParams);
        } catch (YAMLException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "General error while storing experiment, " + e.toString());
        }
        ExperimentParamsYaml epy = stored.atlasParamsYamlWithCache.getExperimentParamsYaml();

        a.name = epy.experimentYaml.name;
        a.description = epy.experimentYaml.description;
        a.code = epy.experimentYaml.code;
        a.createdOn = System.currentTimeMillis();
        a.companyId = sourceCode.companyId;
        final Atlas atlas = atlasRepository.save(a);

        // store all tasks' results
        stored.atlasParamsYamlWithCache.atlasParams.taskIds
                .forEach(id -> {
                    Task t = taskRepository.findById(id).orElse(null);
                    if (t == null) {
                        return;
                    }
                    AtlasTask at = new AtlasTask();
                    at.atlasId = atlas.id;
                    at.taskId = t.getId();
                    AtlasTaskParamsYaml atpy = new AtlasTaskParamsYaml();
                    atpy.assignedOn = t.getAssignedOn();
                    atpy.completed = t.isCompleted();
                    atpy.completedOn = t.getCompletedOn();
                    atpy.execState = t.getExecState();
                    atpy.taskId = t.getId();
                    atpy.taskParams = t.getParams();
                    // typeAsString will be initialized when AtlasTaskParamsYaml will be requested
                    // see method ai.metaheuristic.ai.mh.dispatcher..atlas.AtlasTopLevelService.findTasks
                    atpy.typeAsString = null;
                    atpy.functionExecResults = t.getFunctionExecResults();
                    atpy.metrics = t.getMetrics();

                    at.params = AtlasTaskParamsYamlUtils.BASE_YAML_UTILS.toString(atpy);
                    atlasTaskRepository.save(at);

                });


        execContextFSM.toFinished(execContextId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public StoredToAtlasWithStatus toExperimentStoredToAtlas(SourceCodeImpl sourceCode, ExecContextImpl execContext, Experiment experiment) {
        AtlasParamsYaml atlasParamsYaml = new AtlasParamsYaml();
        atlasParamsYaml.createdOn = System.currentTimeMillis();
        atlasParamsYaml.sourceCode = new AtlasParamsYaml.SourceCodeWithParams(sourceCode.id, sourceCode.getParams());
        atlasParamsYaml.execContext = new AtlasParamsYaml.ExecContextWithParams(execContext.id, execContext.getParams(), EnumsApi.ExecContextState.EXPORTED_TO_ATLAS.code);
        atlasParamsYaml.experiment = new AtlasParamsYaml.ExperimentWithParams(experiment.id, experiment.getParams());
        atlasParamsYaml.taskIds = taskRepository.findAllTaskIdsByExecContextId(execContext.getId());

        StoredToAtlasWithStatus result = new StoredToAtlasWithStatus();
        result.atlasParamsYamlWithCache = new AtlasParamsYamlWithCache( atlasParamsYaml );
        result.status = Enums.StoringStatus.OK;
        return result;
    }
}
