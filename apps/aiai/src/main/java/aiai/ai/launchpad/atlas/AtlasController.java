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

package aiai.ai.launchpad.atlas;

import aiai.ai.launchpad.data.AtlasData;
import aiai.ai.launchpad.data.ExperimentData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.experiment.ExperimentCache;
import aiai.ai.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/launchpad/atlas")
@Slf4j
@Profile("launchpad")
public class AtlasController {

    private final AtlasService atlasService;

    public AtlasController(AtlasService atlasService, ExperimentCache experimentCache) {
        this.atlasService = atlasService;
    }

    @GetMapping("/atlas-experiments")
    public String init(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        AtlasData.AtlasSimpleExperiments atlasExperiments = atlasService.getAtlasExperiments(pageable);
        ControllerUtils.addMessagesToModel(model, atlasExperiments);
        model.addAttribute("result", atlasExperiments);
        return "launchpad/atlas/atlas-experiments";
    }

    // for AJAX
    @PostMapping("/atlas-experiments-part")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable) {
        AtlasData.AtlasSimpleExperiments atlasExperiments = atlasService.getAtlasExperiments(pageable);
        model.addAttribute("result", atlasExperiments);
        return "launchpad/atlas/atlas-experiments :: table";
    }

/*
    @GetMapping(value = "/atlas-experiment-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @ModelAttribute("errorMessage") final String errorMessage ) {
        ExperimentData.ExperimentInfoExtendedResult result =
                AtlasTopLevelService.getExperimentInfo(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return "redirect:/launchpad/atlas/atlas-experiments";
        }

        if (result.isInfoMessages()) {
            model.addAttribute("infoMessages", result.infoMessages);
        }

        model.addAttribute("experiment", result.experiment);
        model.addAttribute("experimentResult", result.experimentInfo);
        return "launchpad/atlas/atlas-experiment-info";
    }

    @GetMapping("/atlas-experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ExperimentData.ExperimentResult result = AtlasTopLevelService.getExperiment(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return "redirect:/launchpad/atlas/atlas-experiments";
        }
        model.addAttribute("experiment", result.experiment);
        return "launchpad/atlas/atlas-experiment-delete";
    }

    @PostMapping("/atlas-experiment-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = AtlasTopLevelService.experimentDeleteCommit(id);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/atlas/atlas-experiments";
    }

*/


}
