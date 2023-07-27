/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.GlobalVariableData;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTopLevelService;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RestController
@RequestMapping("/rest/v1/dispatcher/global-variable")
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class GlobalVariableRestController {

    private final GlobalVariableTopLevelService globalVariableTopLevelService;

    @GetMapping("/global-variables")
    public GlobalVariableData.GlobalVariablesResult getResources(@PageableDefault(size = 5) Pageable pageable) {
        return globalVariableTopLevelService.getGlobalVariables(pageable);
    }

    @PostMapping(value = "/global-variable-upload-from-file", headers = ("content-type=multipart/*"), produces = "application/json", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OperationStatusRest createResourceFromFile(
            @RequestPart MultipartFile file,
            @RequestParam(name = "variable") String variable ) {
        return globalVariableTopLevelService.createGlobalVariableFromFile(file, variable);
    }

    @PostMapping(value = "/global-variable-in-external-storage")
    public OperationStatusRest registerResourceInExternalStorage(
            @RequestParam(name = "variable") String variable,
            @RequestParam(name = "params") String params ) {
        return globalVariableTopLevelService.createGlobalVariableWithExternalStorage(variable, params);
    }

    @GetMapping("/global-variable/{id}")
    public GlobalVariableData.GlobalVariableResult get(@PathVariable Long id) {
        return globalVariableTopLevelService.getGlobalVariableById(id);
    }

    @PostMapping("/global-variable-delete-commit")
    public OperationStatusRest deleteResource(Long id) {
        return globalVariableTopLevelService.deleteGlobalVariable(id);
    }


    @PostMapping(value = "/global-variable-with-value")
    public String createGlobalVariableWithValue(
            @Nullable @RequestParam(name = "variable") String variable,
            @Nullable @RequestParam(name = "value") String value,
            final RedirectAttributes redirectAttributes) {

        OperationStatusRest operationStatusRest = globalVariableTopLevelService.createGlobalVariableWithValue(variable, value);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/global-variable/global-variables";
    }

    // ============= Service methods =============

    @PostMapping(value = "/store-initial-global-variable", headers = ("content-type=multipart/*"), produces = "application/json", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OperationStatusRest storeInitialResource(
            @RequestPart MultipartFile file,
            @SuppressWarnings("unused") @RequestParam(name = "code") String resourceCode,
            @RequestParam(name = "poolCode") String variable,
            @RequestParam(name = "filename") String filename
    ) {
        return globalVariableTopLevelService.storeInitialGlobalVariable(file, variable, filename);
    }

}
