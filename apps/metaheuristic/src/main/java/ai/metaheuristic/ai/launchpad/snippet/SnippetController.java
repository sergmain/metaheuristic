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
package ai.metaheuristic.ai.launchpad.snippet;

import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.SnippetData;
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
@RequestMapping("/launchpad/snippet")
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class SnippetController {

    private static final String REDIRECT_LAUNCHPAD_SNIPPETS = "redirect:/launchpad/snippet/snippets";

    private final SnippetTopLevelService snippetTopLevelService;

    @GetMapping("/snippets")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String init(Model model,
                       @ModelAttribute("errorMessage") final String errorMessage,
                       @ModelAttribute("infoMessages") final String infoMessages) {
        SnippetData.SnippetsResult snippetsResult = snippetTopLevelService.getSnippets();
        ControllerUtils.addMessagesToModel(model, snippetsResult);
        model.addAttribute("result", snippetsResult);
        return "launchpad/snippet/snippets";
    }

    @GetMapping("/snippet-delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public HttpEntity<String> deleteCommit(@PathVariable Long id) {
        OperationStatusRest operationStatusRest = snippetTopLevelService.deleteSnippetById(id);
        if (operationStatusRest.isErrorMessages()) {
            return new HttpEntity<>("false");
        }
        return new HttpEntity<>("true");
    }

    @PostMapping(value = "/snippet-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String uploadSnippet(final MultipartFile file, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = snippetTopLevelService.uploadSnippet(file);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_SNIPPETS;
    }
}
