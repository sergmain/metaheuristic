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
package aiai.ai.launchpad.snippet;

import ai.metaheuristic.api.v1.data.OperationStatusRest;
import aiai.ai.launchpad.data.SnippetData;
import aiai.ai.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/launchpad")
@Slf4j
@Profile("launchpad")
public class SnippetController {

    private static final String REDIRECT_LAUNCHPAD_SNIPPETS = "redirect:/launchpad/snippets";

    private final SnippetTopLevelService snippetTopLevelService;

    public SnippetController(SnippetTopLevelService snippetTopLevelService) {
        this.snippetTopLevelService = snippetTopLevelService;
    }

    @GetMapping("/snippets")
    public String init(Model model,
                       @ModelAttribute("errorMessage") final String errorMessage,
                       @ModelAttribute("infoMessages") final String infoMessages) {
        SnippetData.SnippetsResult snippetsResult = snippetTopLevelService.getSnippets();
        ControllerUtils.addMessagesToModel(model, snippetsResult);
        model.addAttribute("result", snippetsResult);
        return "launchpad/snippets";
    }

    @GetMapping("/snippet-delete/{id}")
    public HttpEntity<String> deleteCommit(@PathVariable Long id) {
        OperationStatusRest operationStatusRest = snippetTopLevelService.deleteSnippetById(id);
        if (operationStatusRest.isErrorMessages()) {
            return new HttpEntity<>("false");
        }
        return new HttpEntity<>("true");
    }

    @PostMapping(value = "/snippet-upload-from-file")
    public String uploadSnippet(final MultipartFile file, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = snippetTopLevelService.uploadSnippet(file);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_SNIPPETS;
    }
}
