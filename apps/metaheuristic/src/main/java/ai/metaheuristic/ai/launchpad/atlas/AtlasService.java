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
import ai.metaheuristic.ai.launchpad.beans.*;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.data.AtlasData;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentCache;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.*;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.launchpad.BinaryData;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasService {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final PlanCache planCache;
    private final WorkbookRepository workbookRepository;
    private final ExperimentCache experimentCache;
    private final TaskRepository taskRepository;
    private final ConsoleFormAtlasService consoleFormAtlasService;
    private final AtlasRepository atlasRepository;

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class StoredToAtlasWithStatus extends BaseDataClass {
        public ExperimentStoredToAtlas experimentStoredToAtlas;
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

    public OperationStatusRest toAtlas(long workbookId, long experimentId) {
        StoredToAtlasWithStatus stored = toExperimentStoredToAtlas(experimentId);
        if (stored.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, stored.errorMessages);
        }
        if (workbookId!=stored.experimentStoredToAtlas.workbook.id) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Experiment can't be stored, workbookId is different");
        }
        String poolCode = getPoolCodeForExperiment(workbookId, experimentId);
        List<SimpleCodeAndStorageUrl> codes = binaryDataService.getResourceCodesInPool(List.of(poolCode), workbookId);
        if (!codes.isEmpty()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Experiment already stored");
        }
        Atlas b = new Atlas();
        try {
            b.experiment = toJson(stored.experimentStoredToAtlas);
        } catch (JsonProcessingException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "General error while storing experiment, " + e.toString());
        }
        ExperimentParamsYaml params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(stored.experimentStoredToAtlas.experiment.getParams());


        b.name = params.yaml.getName();
        b.description = params.yaml.getDescription();
        b.code = stored.experimentStoredToAtlas.experiment.getCode();
        b.createdOn = params.processing.createdOn;
        atlasRepository.save(b);

        ConsoleOutputStoredToAtlas filed = toConsoleOutputStoredToAtlas(stored.experimentStoredToAtlas.workbook.id);
        if (filed.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, filed.errorMessages);
        }
        try(InputStream is = new FileInputStream(filed.dumpOfConsoleOutputs)) {
            //noinspection unused
            BinaryData data = binaryDataService.save(
                    is, filed.dumpOfConsoleOutputs.length(), EnumsApi.BinaryDataType.CONSOLE,
                    poolCode, poolCode, false, null, null, null);

        } catch (FileNotFoundException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "A problem with stored console outputs, try to run again");
        } catch (RuntimeException | IOException e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "Error storing console outputs to db, " + e.toString());
        }

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
        Workbook workbook = workbookRepository.findById(experiment.workbookId).orElse(null);
        if (workbook==null) {
            return new StoredToAtlasWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.05 can't find workbook for this experiment");
        }
        Plan plan = planCache.findById(workbook.getPlanId());
        if (plan==null) {
            return new StoredToAtlasWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.10 can't find plan for this experiment");
        }
        StoredToAtlasWithStatus result = new StoredToAtlasWithStatus();

        List<Task> tasks = taskRepository.findAllByWorkbookId(workbook.getId());

        result.experimentStoredToAtlas = new ExperimentStoredToAtlas( plan, workbook, experiment, tasks);
        result.status = Enums.StoringStatus.OK;
        return result;
    }

    // TODO 2019-06-23 change to yaml format
    public ExperimentStoredToAtlas fromJson(String json) throws IOException {
        //noinspection UnnecessaryLocalVariable,SpellCheckingInspection
        ExperimentStoredToAtlas estb = mapper.readValue(json, ExperimentStoredToAtlas.class);
        return estb;
    }

    // TODO 2019-06-23 change to yaml format
    public String toJson(ExperimentStoredToAtlas stored) throws JsonProcessingException {
        //noinspection UnnecessaryLocalVariable
        String json = mapper.writeValueAsString(stored);
        return json;
    }

    @SuppressWarnings("WeakerAccess")
    public ConsoleOutputStoredToAtlas toConsoleOutputStoredToAtlas(long workbookId) {
        //noinspection UnnecessaryLocalVariable
        ConsoleOutputStoredToAtlas result = consoleFormAtlasService.collectConsoleOutputs(workbookId);
        return result;
    }
}
