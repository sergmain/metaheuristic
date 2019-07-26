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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.launchpad.experiment.ExperimentTopLevelService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/launchpad/experiment")
@Slf4j
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ExperimentRestController {

    private final ExperimentTopLevelService experimentTopLevelService;
    private final WorkbookService workbookService;

    @GetMapping("/experiments")
    public ExperimentApiData.ExperimentsResult getExperiments(@PageableDefault(size = 5) Pageable pageable) {
        return experimentTopLevelService.getExperiments(pageable);
    }

    @GetMapping(value = "/experiment/{id}")
    public ExperimentApiData.ExperimentResult getExperiment(@PathVariable Long id) {
        return experimentTopLevelService.getExperimentWithoutProcessing(id);
    }

    @PostMapping("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    public @ResponseBody
    ExperimentApiData.PlotData getPlotData(
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return experimentTopLevelService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    @PostMapping("/experiment-feature-progress-console-part/{taskId}")
    public ExperimentApiData.ConsoleResult getTasksConsolePart(@PathVariable(name="taskId") Long taskId) {
        return experimentTopLevelService.getTasksConsolePart(taskId);
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
    public ExperimentApiData.ExperimentFeatureExtendedResult getFeatureProgressPart(@PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        return experimentTopLevelService.getFeatureProgressPart(experimentId, featureId, params, pageable);
    }

    @GetMapping(value = "/experiment-feature-progress/{experimentId}/{featureId}")
    public ExperimentApiData.ExperimentFeatureExtendedResult getFeatures(@PathVariable Long experimentId, @PathVariable Long featureId) {
        return experimentTopLevelService.getExperimentFeatureExtended(experimentId, featureId);
    }

    @GetMapping(value = "/experiment-info/{id}")
    public ExperimentApiData.ExperimentInfoExtendedResult info(@PathVariable Long id) {
        return experimentTopLevelService.getExperimentInfo(id);
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public ExperimentApiData.ExperimentsEditResult edit(@PathVariable Long id) {
        return experimentTopLevelService.editExperiment(id);
    }

    @PostMapping("/experiment-add-commit")
    public OperationStatusRest addFormCommit(@RequestBody ExperimentApiData.ExperimentData experiment) {
        return experimentTopLevelService.addExperimentCommit(experiment);
    }

    @PostMapping("/experiment-edit-commit")
    public OperationStatusRest editFormCommit(@RequestBody ExperimentApiData.SimpleExperiment simpleExperiment) {
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

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{key}")
    public OperationStatusRest metadataDeleteCommit(@PathVariable Long experimentId, @PathVariable String key) {
        if (true) throw new IllegalStateException("Need to change this in web(html files) and angular");
        return experimentTopLevelService.metadataDeleteCommit(experimentId, key);
    }

    @GetMapping("/experiment-metadata-default-add-commit/{experimentId}")
    public OperationStatusRest metadataDefaultAddCommit(@PathVariable Long experimentId) {
        return experimentTopLevelService.metadataDefaultAddCommit(experimentId);
    }

    @GetMapping("/experiment-snippet-delete-commit/{experimentId}/{snippetCode}")
    public OperationStatusRest snippetDeleteCommit(@PathVariable Long experimentId, @PathVariable String snippetCode) {
        return experimentTopLevelService.snippetDeleteCommit(experimentId, snippetCode);
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
        return workbookService.resetTask(taskId);
    }

    @PostMapping(value = "/experiment-upload-from-file")
    public OperationStatusRest uploadSnippet(final MultipartFile file) {
        return experimentTopLevelService.uploadExperiment(file);
    }

    @GetMapping(value = "/experiment-to-atlas/{id}")
    public OperationStatusRest toAtlas(@PathVariable Long id) {
        return experimentTopLevelService.toAtlas(id);
    }
}
