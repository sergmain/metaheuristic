/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.launchpad.snippet;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.repositories.SnippetRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/launchpad")
@Slf4j
public class SnippetController {

    private final Globals globals;
    private final SnippetRepository snippetRepository;

    @Data
    public static class Result {
        Iterable<Snippet> snippets;
    }

    public SnippetController(Globals globals, SnippetRepository snippetRepository) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
    }

    @GetMapping("/snippets")
    public String init(@ModelAttribute Result result, @ModelAttribute("errorMessage") final String errorMessage) {
        result.snippets = snippetRepository.findAll();
        return "launchpad/snippets";
    }

    @PostMapping(value = "/snippet-upload-from-file")
    public String uploadSnippet(MultipartFile file, final RedirectAttributes redirectAttributes) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#22.01 name of uploaded file is null");
            return "redirect:/launchpad/snippets";
        }
        int idx;
        if ((idx = originFilename.lastIndexOf('.')) == -1) {
            redirectAttributes.addFlashAttribute("errorMessage", "#22.02 '.' wasn't found, bad filename: " + originFilename);
            return "redirect:/launchpad/snippets";
        }
        String ext = originFilename.substring(idx).toLowerCase();
        if (!".zip".equals(ext)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#22.03 only '.zip' files is supported, filename: " + originFilename);
            return "redirect:/launchpad/snippets";
        }
/*
        try {
            File dir = DirUtils.getTempFile(file);
            if (!dir.exists()) {
                dir.mkdir();
            }
            ZipUtils.unzipFolder(file, dir);
            executorsForProcessing.put(dir);
        }
        catch (Exception e) {
            throw new RuntimeException("Error, file: " + file, e);
        }

        Snippet snippet = new Snippet();
        snippet.setCode();

        file.
        Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#72.02 dataset wasn't found for id " + datasetId);
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }

        try (InputStream is = file.getInputStream()) {
            storeNewPartOfRawFile(originFilename, dataset, is, true);
        }
        catch (IOException e) {
            throw new RuntimeException("error", e);
        }

*/
        return "redirect:/launchpad/snippets";
    }
}
