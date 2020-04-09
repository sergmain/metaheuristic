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

package ai.metaheuristic.ai.dispatcher.variable_global;

import ai.metaheuristic.ai.dispatcher.data.GlobalVariableData;
import ai.metaheuristic.api.data.OperationStatusRest;
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
@RequestMapping("/dispatcher/global-variable")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class GlobalVariableController {

    private final GlobalVariableTopLevelService globalVariableTopLevelService;

    @GetMapping("/global-variables")
    public String init(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessages) {
        GlobalVariableData.GlobalVariablesResult globalVariablesResultRest = globalVariableTopLevelService.getGlobalVariables(pageable);
        ControllerUtils.addMessagesToModel(model, globalVariablesResultRest);
        model.addAttribute("result", globalVariablesResultRest);
        return "dispatcher/global-variable/global-variables";
    }

    // for AJAX
    @PostMapping("/global-variables-part")
    public String getGlobalVariablesForAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        GlobalVariableData.GlobalVariablesResult globalVariablesResultRest = globalVariableTopLevelService.getGlobalVariables(pageable);
        model.addAttribute("result", globalVariablesResultRest);
        return "dispatcher/global-variable/global-variables :: fragment-table";
    }

    @PostMapping(value = "/global-variable-upload-from-file")
    public String createGlobalVariableFromFile(
            MultipartFile file,
            @RequestParam(name = "variable") String variable,
            final RedirectAttributes redirectAttributes) {

        OperationStatusRest operationStatusRest = globalVariableTopLevelService.createGlobalVariableFromFile(file, variable);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/global-variable/global-variables";
    }

    @PostMapping(value = "/global-variable-in-external-storage")
    public String registerGlobalVariableInExternalStorage(
            @RequestParam(name = "variable") String variable,
            @RequestParam(name = "params") String params,
            final RedirectAttributes redirectAttributes) {

        OperationStatusRest operationStatusRest = globalVariableTopLevelService.createGlobalVariableWithExternalStorage(variable, params);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/global-variable/global-variables";
    }

    @GetMapping("/global-variable-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        GlobalVariableData.GlobalVariableResult globalVariableResultRest = globalVariableTopLevelService.getGlobalVariableById(id);
        if (globalVariableResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", globalVariableResultRest.getErrorMessagesAsList());
            return "redirect:/dispatcher/global-variable/global-variables";
        }
        model.addAttribute("globalVariable", globalVariableResultRest.data);
        return "dispatcher/global-variable/global-variable-delete";
    }

    @PostMapping("/global-variable-delete-commit")
    public String deleteGlobalVariable(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = globalVariableTopLevelService.deleteGlobalVariable(id);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/global-variable/global-variables";
    }
}
