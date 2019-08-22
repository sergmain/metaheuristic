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

import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.atlas.AtlasTopLevelService;
import ai.metaheuristic.ai.launchpad.data.AtlasData;
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
@RequestMapping("/rest/v1/launchpad/atlas")
@Slf4j
@Profile("launchpad")
@CrossOrigin
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class AtlasRestController {

    private final AtlasService atlasService;
    private final AtlasTopLevelService atlasTopLevelService;

    @GetMapping("/atlas-experiments")
    public AtlasData.AtlasSimpleExperiments init(@PageableDefault(size = 5) Pageable pageable) {
        return atlasService.getAtlasExperiments(pageable);
    }

    @GetMapping(value = "/atlas-experiment-info/{id}")
    public AtlasData.ExperimentInfoExtended info(@PathVariable Long id) {
        return atlasTopLevelService.getExperimentInfoExtended(id);
    }

    @PostMapping("/atlas-experiment-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return atlasTopLevelService.atlasDeleteCommit(id);
    }

    @GetMapping(value = "/atlas-experiment-feature-progress/{atlasId}/{experimentId}/{featureId}")
    public AtlasData.ExperimentFeatureExtendedResult getFeatures(
            @PathVariable Long atlasId,@PathVariable Long experimentId, @PathVariable Long featureId) {
        return atlasTopLevelService.getExperimentFeatureExtended(atlasId, experimentId, featureId);
    }

    @PostMapping("/atlas-experiment-feature-plot-data-part/{atlasId}/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    @ResponseBody
    public AtlasData.PlotData getPlotData(
            @PathVariable Long atlasId,
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return atlasTopLevelService.getPlotData(atlasId, experimentId, featureId, params, paramsAxis);
    }

    @PostMapping("/atlas-experiment-feature-progress-console-part/{atlasId}/{taskId}")
    public AtlasData.ConsoleResult getTasksConsolePart(@PathVariable(name = "atlasId") Long atlasId, @PathVariable(name = "taskId") Long taskId ) {
        return atlasTopLevelService.getTasksConsolePart(atlasId, taskId);
    }

    @PostMapping("/atlas-experiment-feature-progress-part/{atlasId}/{experimentId}/{featureId}/{params}/part")
    public AtlasData.ExperimentFeatureExtendedResult getFeatureProgressPart(@PathVariable Long atlasId, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        return atlasTopLevelService.getFeatureProgressPart(atlasId, featureId, params, pageable);
    }

    @PostMapping(value = "/atlas-experiment-upload-from-file")
    public OperationStatusRest uploadAtlas(final MultipartFile file) {
        return atlasTopLevelService.uploadExperiment(file);
    }
}
