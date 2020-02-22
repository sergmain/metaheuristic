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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dispatcher/function")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionController {

    private static final String REDIRECT_LAUNCHPAD_FUNCTIONS = "redirect:/dispatcher/function/functions";

    private final FunctionTopLevelService functionTopLevelService;

    @GetMapping("/functions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String init(Model model,
                       @ModelAttribute("errorMessage") final String errorMessage,
                       @ModelAttribute("infoMessages") final String infoMessages) {
        FunctionData.FunctionsResult functionsResult = functionTopLevelService.getFunctions();
        ControllerUtils.addMessagesToModel(model, functionsResult);
        model.addAttribute("result", functionsResult);
        return "dispatcher/function/functions";
    }

    @GetMapping("/function-delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public HttpEntity<String> deleteCommit(@PathVariable Long id) {
        OperationStatusRest operationStatusRest = functionTopLevelService.deleteFunctionById(id);
        if (operationStatusRest.isErrorMessages()) {
            return new HttpEntity<>("false");
        }
        return new HttpEntity<>("true");
    }

    @PostMapping(value = "/function-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String uploadFunction(final MultipartFile file, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = functionTopLevelService.uploadFunction(file);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_FUNCTIONS;
    }
}
