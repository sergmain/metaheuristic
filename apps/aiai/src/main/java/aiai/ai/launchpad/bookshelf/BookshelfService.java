/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.bookshelf;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import aiai.ai.launchpad.data.BaseDataClass;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.experiment.ExperimentCache;
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.repositories.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
public class BookshelfService {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    private final BinaryDataService binaryDataService;
    private final FlowCache flowCache;
    private final FlowInstanceRepository flowInstanceRepository;
    private final ExperimentCache experimentCache;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentTaskFeatureRepository experimentTaskFeatureRepository;
    private final TaskRepository taskRepository;
    private final ConsoleFormBookshelfService consoleFormBookshelfService;
    private final BookshelfRepository bookshelfRepository;

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class StoredToBookshelfWithStatus extends BaseDataClass {
        public ExperimentStoredToBookshelf experimentStoredToBookshelf;
        public Enums.StoringStatus status;

        @SuppressWarnings("WeakerAccess")
        public StoredToBookshelfWithStatus(Enums.StoringStatus status, String errorMessage) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }
    }

    @Autowired
    public BookshelfService(FlowCache flowCache, FlowInstanceRepository flowInstanceRepository, ExperimentCache experimentCache, ExperimentFeatureRepository experimentFeatureRepository, BinaryDataService binaryDataService, ExperimentSnippetRepository experimentSnippetRepository, ExperimentTaskFeatureRepository experimentTaskFeatureRepository, TaskRepository taskRepository, ConsoleFormBookshelfService consoleFormBookshelfService, BookshelfRepository bookshelfRepository) {
        this.flowCache = flowCache;
        this.flowInstanceRepository = flowInstanceRepository;
        this.experimentCache = experimentCache;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.binaryDataService = binaryDataService;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentTaskFeatureRepository = experimentTaskFeatureRepository;
        this.taskRepository = taskRepository;
        this.consoleFormBookshelfService = consoleFormBookshelfService;
        this.bookshelfRepository = bookshelfRepository;
    }

    public OperationStatusRest toBookshelf(long flowInstanceId, long experimentId) {
        StoredToBookshelfWithStatus stored = toExperimentStoredToBookshelf(experimentId);
        if (stored.isErrorMessages()) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, stored.errorMessages);
        }
        if (flowInstanceId!=stored.experimentStoredToBookshelf.flowInstance.id) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "Experiment can't be stored, flowInstanceId is different");
        }
        String poolCode = getPoolCodeForExperiment(flowInstanceId, experimentId);
        List<SimpleCodeAndStorageUrl> codes = binaryDataService.getResourceCodesInPool(List.of(poolCode));
        if (!codes.isEmpty()) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "Experiment already stored");
        }
        Bookshelf b = new Bookshelf();
        try {
            b.experiment = toJson(stored.experimentStoredToBookshelf);
        } catch (JsonProcessingException e) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "General error while storing experiment, " + e.toString());
        }
        b.name = stored.experimentStoredToBookshelf.experiment.getName();
        b.description = stored.experimentStoredToBookshelf.experiment.getDescription();
        b.code = stored.experimentStoredToBookshelf.experiment.getCode();
        bookshelfRepository.save(b);

        ConsoleOutputStoredToBookshelf filed = toConsoleOutputStoredToBookshelf(
                stored.experimentStoredToBookshelf.flowInstance.id);
        if (filed.isErrorMessages()) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, filed.errorMessages);
        }
        try(InputStream is = new FileInputStream(filed.dumpOfConsoleOutputs)) {
//            public BinaryData save(InputStream is, long size,
//            Enums.BinaryDataType binaryDataType, String code, String poolCode,
//            boolean isManual, String filename, Long flowInstanceId) {
            //noinspection unused
            BinaryData data = binaryDataService.save(
                    is, filed.dumpOfConsoleOutputs.length(), Enums.BinaryDataType.CONSOLE,
                    poolCode, poolCode, false, null, null);

        } catch (FileNotFoundException e) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "A problem with stored console outputs, try to run again");
        } catch (RuntimeException | IOException e) {
            log.error("Error", e);
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "Error storing console outputs to db, " + e.toString());
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @SuppressWarnings("WeakerAccess")
    public static String getPoolCodeForExperiment(long flowInstanceId, long experimentId) {
        return String.format("stored-experiment-%d-%d",flowInstanceId, experimentId);
    }

    public StoredToBookshelfWithStatus toExperimentStoredToBookshelf(long experimentId) {

        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment==null) {
            return new StoredToBookshelfWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.02 can't find experiment for id: " + experimentId);
        }
        FlowInstance flowInstance = flowInstanceRepository.findById(experiment.flowInstanceId).orElse(null);
        if (flowInstance==null) {
            return new StoredToBookshelfWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.05 can't find flowInstance for this experiment");
        }
        Flow flow = flowCache.findById(flowInstance.flowId);
        if (flow==null) {
            return new StoredToBookshelfWithStatus(Enums.StoringStatus.CANT_BE_STORED,
                    "#604.10 can't find flow for this experiment");
        }
        StoredToBookshelfWithStatus result = new StoredToBookshelfWithStatus();

        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experimentId);
        List<ExperimentSnippet> snippets = experimentSnippetRepository.findByExperimentId(experimentId);
        List<ExperimentTaskFeature> taskFeatures = experimentTaskFeatureRepository.findByFlowInstanceId(flowInstance.id);
        List<Task> tasks = taskRepository.findAllByFlowInstanceId(flowInstance.id);

        result.experimentStoredToBookshelf = new ExperimentStoredToBookshelf(
                flow, flowInstance, experiment,
                features, experiment.hyperParams, snippets, taskFeatures, tasks
        );
        result.status = Enums.StoringStatus.OK;
        return result;
    }

    public String toJson(ExperimentStoredToBookshelf stored) throws JsonProcessingException {
        //noinspection UnnecessaryLocalVariable
        String json = mapper.writeValueAsString(stored);
        return json;
    }

    @SuppressWarnings("WeakerAccess")
    public ConsoleOutputStoredToBookshelf toConsoleOutputStoredToBookshelf(long flowInstanceId) {
        //noinspection UnnecessaryLocalVariable
        ConsoleOutputStoredToBookshelf result = consoleFormBookshelfService.collectConsoleOutputs(
                flowInstanceId);
        return result;
    }
}
