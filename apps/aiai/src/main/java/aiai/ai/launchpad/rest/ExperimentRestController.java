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

package aiai.ai.launchpad.rest;

import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.experiment.ExperimentTopLevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import static aiai.ai.launchpad.data.ExperimentData.*;

@SuppressWarnings("Duplicates")
@RestController
@RequestMapping("/ng/launchpad/experiment")
@Slf4j
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class ExperimentRestController {

    private final ExperimentTopLevelService experimentTopLevelService;

    public ExperimentRestController(ExperimentTopLevelService experimentTopLevelService) {
        this.experimentTopLevelService = experimentTopLevelService;
    }

    @GetMapping("/experiments")
    public ExperimentsResult getExperiments(Pageable pageable) {
        return experimentTopLevelService.getExperiments(pageable);
    }

    @GetMapping(value = "/experiment/{id}")
    public ExperimentResult getExperiment(@PathVariable Long id) {
        return experimentTopLevelService.getExperiment(id);
    }

    @PostMapping("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    public @ResponseBody
    PlotData getPlotData(
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return experimentTopLevelService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    @PostMapping("/experiment-feature-progress-console-part/{taskId}")
    public ConsoleResult getTasksConsolePart(@PathVariable(name="taskId") Long taskId) {
        return experimentTopLevelService.getTasksConsolePart(taskId);
    }

    @GetMapping("/experiment-feature-progress-console/{taskId}")
    public ConsoleResult getTasksConsole(@PathVariable(name="taskId") Long taskId) {
        return experimentTopLevelService.getTasksConsolePart(taskId);
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
    public ExperimentFeatureExtendedResult getFeatureProgressPart(@PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        return experimentTopLevelService.getFeatureProgressPart(experimentId, featureId, params, pageable);
    }

    @GetMapping(value = "/experiment-feature-progress/{experimentId}/{featureId}")
    public ExperimentFeatureExtendedResult getFeatures(@PathVariable Long experimentId, @PathVariable Long featureId) {
        return experimentTopLevelService.getExperimentFeatureExtended(experimentId, featureId);
    }

    @GetMapping(value = "/experiment-info/{id}")
    public ExperimentInfoExtendedResult info(@PathVariable Long id) {
        return experimentTopLevelService.getExperimentInfo(id);
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public ExperimentsEditResult edit(@PathVariable Long id) {
        return experimentTopLevelService.editExperiment(id);
    }

    @PostMapping("/experiment-add-commit")
    public OperationStatusRest addFormCommit(@RequestBody Experiment experiment) {
        return experimentTopLevelService.addExperimentCommit(experiment);
    }

    @PostMapping("/experiment-edit-commit")
    public OperationStatusRest editFormCommit(@RequestBody SimpleExperiment simpleExperiment) {
        return experimentTopLevelService.editExperimentCommit(simpleExperiment);
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public OperationStatusRest metadataAddCommit(@PathVariable Long id, String key, String value) {
        return experimentTopLevelService.metadataAddCommit(id, key, value);
    }

    @PostMapping("/experiment-metadata-edit-commit/{id}")
    public OperationStatusRest metadataEditCommit(@PathVariable Long id, String key, String value) {
        return experimentTopLevelService.metadataEditCommit(id, key, value);
    }

    @PostMapping("/experiment-snippet-add-commit/{id}")
    public OperationStatusRest snippetAddCommit(@PathVariable Long id, String code) {
        return experimentTopLevelService.snippetAddCommit(id, code);
    }

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{id}")
    public OperationStatusRest metadataDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        return experimentTopLevelService.metadataDeleteCommit(experimentId, id);
    }

    @GetMapping("/experiment-metadata-default-add-commit/{experimentId}")
    public OperationStatusRest metadataDefaultAddCommit(@PathVariable long experimentId) {
        return experimentTopLevelService.metadataDefaultAddCommit(experimentId);
    }

    @GetMapping("/experiment-snippet-delete-commit/{experimentId}/{id}")
    public OperationStatusRest snippetDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        return experimentTopLevelService.snippetDeleteCommit(experimentId, id);
    }

    @PostMapping("/experiment-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return experimentTopLevelService.experimentDeleteCommit(id);
    }

    @PostMapping("/experiment-clone-commit")
    public OperationStatusRest experimentCloneCommit(Long id) {
        return experimentTopLevelService.experimentCloneCommit(id);
    }

    @PostMapping("/task-rerun/{taskId}")
    public OperationStatusRest rerunTask(@PathVariable long taskId) {
        return experimentTopLevelService.rerunTask(taskId);
    }
}
