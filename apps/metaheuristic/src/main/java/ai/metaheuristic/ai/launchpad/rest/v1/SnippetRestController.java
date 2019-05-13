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

import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.SnippetData;
import ai.metaheuristic.ai.launchpad.snippet.SnippetTopLevelService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/launchpad/snippet")
@Profile("launchpad")
@CrossOrigin
public class SnippetRestController {

    private final SnippetTopLevelService snippetTopLevelService;

    public SnippetRestController(SnippetTopLevelService snippetTopLevelService) {
        this.snippetTopLevelService = snippetTopLevelService;
    }

    @GetMapping("/snippets")
    public SnippetData.SnippetsResult getSnippets() {
        return snippetTopLevelService.getSnippets();
    }

    @GetMapping("/snippet-delete/{id}")
    public OperationStatusRest deleteCommit(@PathVariable Long id) {
        return snippetTopLevelService.deleteSnippetById(id);
    }

    @PostMapping(value = "/snippet-upload-from-file")
    public OperationStatusRest uploadSnippet(final MultipartFile file) {
        return snippetTopLevelService.uploadSnippet(file);
    }

}
