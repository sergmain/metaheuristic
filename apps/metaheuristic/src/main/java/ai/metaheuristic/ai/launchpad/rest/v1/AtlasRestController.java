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
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/v1/launchpad/atlas")
@Slf4j
@Profile("launchpad")
@CrossOrigin
public class AtlasRestController {

    private final AtlasService atlasService;
    private final AtlasTopLevelService atlasTopLevelService;

    public AtlasRestController(AtlasService atlasService, AtlasTopLevelService atlasTopLevelService) {
        this.atlasService = atlasService;
        this.atlasTopLevelService = atlasTopLevelService;
    }

    @GetMapping("/atlas-experiments")
    public AtlasData.AtlasSimpleExperiments init(@PageableDefault(size = 5) Pageable pageable) {
        return atlasService.getAtlasExperiments(pageable);
    }

    @GetMapping(value = "/atlas-experiment-info/{id}")
    public AtlasData.ExperimentInfoExtended info(@PathVariable Long id) {
        return atlasTopLevelService.getExperimentInfo(id);
    }

    @PostMapping("/atlas-experiment-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return atlasTopLevelService.experimentDeleteCommit(id);
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
        return atlasTopLevelService.getFeatureProgressPart(atlasId, experimentId, featureId, params, pageable);
    }
}