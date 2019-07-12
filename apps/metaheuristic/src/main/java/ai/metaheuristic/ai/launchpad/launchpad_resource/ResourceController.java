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

package ai.metaheuristic.ai.launchpad.launchpad_resource;

import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.ResourceData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad/resource")
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ResourceController {

    private final ResourceTopLevelService resourceTopLevelService;

    @GetMapping("/resources")
    public String init(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        ResourceData.ResourcesResult resourcesResultRest = resourceTopLevelService.getResources(pageable);
        ControllerUtils.addMessagesToModel(model, resourcesResultRest);
        model.addAttribute("result", resourcesResultRest);
        return "launchpad/resource/resources";
    }

    // for AJAX
    @PostMapping("/resources-part")
    public String getResourcesForAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        ResourceData.ResourcesResult resourcesResultRest = resourceTopLevelService.getResources(pageable);
        model.addAttribute("result", resourcesResultRest);
        return "launchpad/resource/resources :: fragment-table";
    }

    @PostMapping(value = "/resource-upload-from-file")
    public String createResourceFromFile(
            MultipartFile file,
            @RequestParam(name = "code") String resourceCode,
            @RequestParam(name = "poolCode") String resourcePoolCode,
            final RedirectAttributes redirectAttributes) {

        OperationStatusRest operationStatusRest = resourceTopLevelService.createResourceFromFile(file, resourcePoolCode, resourceCode);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return "redirect:/launchpad/resource/resources";
    }

    @PostMapping(value = "/resource-in-external-storage")
    public String registerResourceInExternalStorage(
            @RequestParam(name = "poolCode") String resourcePoolCode,
            @RequestParam(name = "params") String params,
            final RedirectAttributes redirectAttributes) {

        OperationStatusRest operationStatusRest = resourceTopLevelService.registerResourceInExternalStorage(resourcePoolCode, params);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return "redirect:/launchpad/resource/resources";
    }

    @GetMapping("/resource-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ResourceData.ResourceResult resourceResultRest = resourceTopLevelService.getResourceById(id);
        if (resourceResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", resourceResultRest.errorMessages);
            return "redirect:/launchpad/resources";
        }
        model.addAttribute("resource", resourceResultRest.data);
        return "launchpad/resource/resource-delete";
    }

    @PostMapping("/resource-delete-commit")
    public String deleteResource(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = resourceTopLevelService.deleteResource(id);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return "redirect:/launchpad/resource/resources";
    }
}
