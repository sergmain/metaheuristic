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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/dispatcher/experiment-result")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ExperimentResultRestController {

    private final ExperimentResultService experimentResultService;
    private final ExperimentResultTopLevelService experimentResultTopLevelService;

    @GetMapping("/experiment-result-experiments")
    public ExperimentResultData.ExperimentResultSimpleExperiments init(@PageableDefault(size = 5) Pageable pageable) {
        return experimentResultService.getExperimentResultExperiments(pageable);
    }

    @GetMapping(value = "/experiment-result-experiment-info/{id}")
    public ExperimentResultData.ExperimentInfoExtended info(@PathVariable Long id) {
        return experimentResultTopLevelService.getExperimentInfoExtended(id);
    }

    @PostMapping("/experiment-result-experiment-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return experimentResultTopLevelService.experimentResultDeleteCommit(id);
    }

    @GetMapping(value = "/experiment-result-experiment-feature-progress/{experimentResultId}/{experimentId}/{featureId}")
    public ExperimentResultData.ExperimentFeatureExtendedResult getFeatures(
            @PathVariable Long experimentResultId,@PathVariable Long experimentId, @PathVariable Long featureId) {
        return experimentResultTopLevelService.getExperimentFeatureExtended(experimentResultId, experimentId, featureId);
    }

    @PostMapping("/experiment-result-experiment-feature-plot-data-part/{experimentResultId}/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    @ResponseBody
    public ExperimentResultData.PlotData getPlotData(
            @PathVariable Long experimentResultId,
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return experimentResultTopLevelService.getPlotData(experimentResultId, experimentId, featureId, params, paramsAxis);
    }

    @PostMapping("/experiment-result-experiment-feature-progress-console-part/{experimentResultId}/{taskId}")
    public ExperimentResultData.ConsoleResult getTasksConsolePart(@PathVariable(name = "experimentResultId") Long atlasId, @PathVariable(name = "taskId") Long taskId ) {
        return experimentResultTopLevelService.getTasksConsolePart(atlasId, taskId);
    }

    @PostMapping("/experiment-result-experiment-feature-progress-part/{experimentResultId}/{experimentId}/{featureId}/{params}/part")
    public ExperimentResultData.ExperimentFeatureExtendedResult getFeatureProgressPart(@PathVariable Long experimentResultId, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        return experimentResultTopLevelService.getFeatureProgressPart(experimentResultId, featureId, params, pageable);
    }

    @PostMapping(value = "/experiment-result-experiment-upload-from-file")
    public OperationStatusRest uploadExperimentResult(final MultipartFile file) {
        return experimentResultTopLevelService.uploadExperiment(file);
    }
}
