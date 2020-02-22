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

package ai.metaheuristic.ai.dispatcher.atlas;

import ai.metaheuristic.ai.dispatcher.data.AtlasData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/dispatcher/atlas")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class AtlasController {

    private static final String REDIRECT_DISPATCHER_ATLAS_ATLAS_EXPERIMENTS = "redirect:/dispatcher/atlas/atlas-experiments";
    private final AtlasService atlasService;
    private final AtlasTopLevelService atlasTopLevelService;

    @GetMapping("/atlas-experiments")
    public String init(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        AtlasData.AtlasSimpleExperiments atlasExperiments = atlasService.getAtlasExperiments(pageable);
        ControllerUtils.addMessagesToModel(model, atlasExperiments);
        model.addAttribute("result", atlasExperiments);
        return "dispatcher/atlas/atlas-experiments";
    }

    // for AJAX
    @PostMapping("/atlas-experiments-part")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable) {
        AtlasData.AtlasSimpleExperiments atlasExperiments = atlasService.getAtlasExperiments(pageable);
        model.addAttribute("result", atlasExperiments);
        return "dispatcher/atlas/atlas-experiments :: table";
    }

    @GetMapping(value = "/atlas-experiment-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @ModelAttribute("errorMessage") final String errorMessage) {
        AtlasData.ExperimentInfoExtended result = atlasTopLevelService.getExperimentInfoExtended(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return "redirect:/dispatcher/atlas/atlas-experiments";
        }

        if (result.isInfoMessages()) {
            model.addAttribute("infoMessages", result.infoMessages);
        }

        model.addAttribute("atlas", result.atlas);
        model.addAttribute("experiment", result.experiment);
        model.addAttribute("experimentResult", result.experimentInfo);
        return "dispatcher/atlas/atlas-experiment-info";
    }

    @GetMapping("/atlas-experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        AtlasData.ExperimentDataOnly result = atlasTopLevelService.getExperimentDataOnly(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return "redirect:/dispatcher/atlas/atlas-experiments";
        }

        if (result.isInfoMessages()) {
            model.addAttribute("infoMessages", result.infoMessages);
        }

        model.addAttribute("experiment", result.experiment);
        model.addAttribute("atlasId", result.atlasId);
        return "dispatcher/atlas/atlas-experiment-delete";
    }

    @PostMapping("/atlas-experiment-delete-commit")
    public String deleteCommit(Long atlasId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = atlasTopLevelService.atlasDeleteCommit(atlasId);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return REDIRECT_DISPATCHER_ATLAS_ATLAS_EXPERIMENTS;
    }

    @GetMapping(value= "/atlas-experiment-export/atlas-{atlasId}.yaml", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> downloadProcessingResult(@PathVariable("atlasId") Long atlasId) {
        //noinspection UnnecessaryLocalVariable
        ResponseEntity<AbstractResource> res = atlasTopLevelService.exportAtlasToFile(atlasId);
        return res;
    }

    @PostMapping(value = "/atlas-experiment-upload-from-file")
    public String uploadAtlas(final MultipartFile file, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = atlasTopLevelService.uploadExperiment(file);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return REDIRECT_DISPATCHER_ATLAS_ATLAS_EXPERIMENTS;
    }

    @GetMapping(value= "/atlas-experiment-export/{atlasId}")
    public String exportExperiment(Model model, @PathVariable("atlasId") Long atlasId) {
        model.addAttribute("atlasId", atlasId);
        return "dispatcher/atlas/atlas-experiment-export";
    }

    @GetMapping(value= "/atlas-experiment-import")
    public String importExperiment(Model model) {
        return "dispatcher/atlas/atlas-experiment-import";
    }

    @GetMapping(value = "/atlas-experiment-feature-progress/{atlasId}/{experimentId}/{featureId}")
    public String getFeatures(
            Model model,
            @PathVariable Long atlasId,
            @PathVariable Long experimentId,
            @PathVariable Long featureId,
            final RedirectAttributes redirectAttributes) {

        AtlasData.ExperimentFeatureExtendedResult experimentProgressResult =
                atlasTopLevelService.getExperimentFeatureExtended(atlasId, experimentId, featureId);
        if (experimentProgressResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", experimentProgressResult.errorMessages);
            return "redirect:/dispatcher/atlas/atlas-experiment-info/" + atlasId;
        }
        model.addAttribute("metrics", experimentProgressResult.metricsResult);
        model.addAttribute("params", experimentProgressResult.hyperParamResult);
        model.addAttribute("tasks", experimentProgressResult.tasks);
        model.addAttribute("feature", experimentProgressResult.experimentFeature);
        model.addAttribute("consoleResult", experimentProgressResult.consoleResult);
        model.addAttribute("experimentId", experimentId);
        model.addAttribute("atlasId", atlasId);

        return "dispatcher/atlas/atlas-experiment-feature-progress";
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
    public String getTasksConsolePart(
            Model model,
            @PathVariable(name = "atlasId") Long atlasId,
            @PathVariable(name = "taskId") Long taskId
    ) {
        AtlasData.ConsoleResult result = atlasTopLevelService.getTasksConsolePart(atlasId, taskId);
        model.addAttribute("consoleResult", result);
        return "dispatcher/atlas/atlas-experiment-feature-progress :: fragment-console-table";
    }

    @PostMapping("/atlas-experiment-feature-progress-part/{atlasId}/{experimentId}/{featureId}/{params}/part")
    public String getFeatureProgressPart(Model model, @PathVariable Long atlasId, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        AtlasData.ExperimentFeatureExtendedResult experimentProgressResult =
                atlasTopLevelService.getFeatureProgressPart(atlasId, featureId, params, pageable);

        model.addAttribute("metrics", experimentProgressResult.metricsResult);
        model.addAttribute("params", experimentProgressResult.hyperParamResult);
        model.addAttribute("tasks", experimentProgressResult.tasks);
        model.addAttribute("feature", experimentProgressResult.experimentFeature);
        model.addAttribute("consoleResult", experimentProgressResult.consoleResult);
        model.addAttribute("experimentId", experimentId);
        model.addAttribute("atlasId", atlasId);

        return "dispatcher/atlas/atlas-experiment-feature-progress :: fragment-table";
    }


}
