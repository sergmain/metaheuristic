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
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.repositories.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("launchpad")
public class BookshelfService {

    private final FlowCache flowCache;
    private final FlowInstanceRepository flowInstanceRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentTaskFeatureRepository experimentTaskFeatureRepository;
    private final TaskRepository taskRepository;
    private final ConsoleFormBookshelfService consoleFormBookshelfService;

    public static final StoredToBookshelfWithStatus CANT_BE_STORED_ERROR = new StoredToBookshelfWithStatus(Enums.StoringStatus.CANT_BE_STORED);
    public static final ConsoleStoredToBookshelfWithStatus CONSOLE_CANT_BE_STORED_ERROR = new ConsoleStoredToBookshelfWithStatus(Enums.StoringStatus.CANT_BE_STORED);

    @Data
    @NoArgsConstructor
    public static class StoredToBookshelfWithStatus {
        public ExperimentStoredToBookshelf experimentStoredToBookshelf;
        public Enums.StoringStatus status;

        public StoredToBookshelfWithStatus(Enums.StoringStatus status) {
            this.status = status;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ConsoleStoredToBookshelfWithStatus {
        public ConsoleOutputStoredToBookshelf consoleOutputStoredToBookshelf;
        public Enums.StoringStatus status;

        public ConsoleStoredToBookshelfWithStatus(Enums.StoringStatus status) {
            this.status = status;
        }
    }

    @Autowired
    public BookshelfService(FlowCache flowCache, FlowInstanceRepository flowInstanceRepository, ExperimentRepository experimentRepository, ExperimentFeatureRepository experimentFeatureRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, ExperimentSnippetRepository experimentSnippetRepository, ExperimentTaskFeatureRepository experimentTaskFeatureRepository, TaskRepository taskRepository, ConsoleFormBookshelfService consoleFormBookshelfService) {
        this.flowCache = flowCache;
        this.flowInstanceRepository = flowInstanceRepository;
        this.experimentRepository = experimentRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentTaskFeatureRepository = experimentTaskFeatureRepository;
        this.taskRepository = taskRepository;
        this.consoleFormBookshelfService = consoleFormBookshelfService;
    }

    public StoredToBookshelfWithStatus toExperimentStoredToBookshelf(long experimentId) {

        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment==null) {
            return CANT_BE_STORED_ERROR;
        }
        FlowInstance flowInstance = flowInstanceRepository.findById(experiment.flowInstanceId).orElse(null);
        if (flowInstance==null) {
            return CANT_BE_STORED_ERROR;
        }
        Flow flow = flowCache.findById(flowInstance.flowId);
        if (flow==null) {
            return CANT_BE_STORED_ERROR;
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

    @SuppressWarnings("Duplicates")
    public ConsoleStoredToBookshelfWithStatus toConsoleOutputStoredToBookshelf(long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment==null) {
            return CONSOLE_CANT_BE_STORED_ERROR;
        }
        FlowInstance flowInstance = flowInstanceRepository.findById(experiment.flowInstanceId).orElse(null);
        if (flowInstance==null) {
            return CONSOLE_CANT_BE_STORED_ERROR;
        }
        ConsoleStoredToBookshelfWithStatus result = new ConsoleStoredToBookshelfWithStatus();

        result.consoleOutputStoredToBookshelf = consoleFormBookshelfService.collectConsoleOutputs(flowInstance.id);

        return result;
    }
}
