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
import aiai.ai.launchpad.beans.SnippetBase;
import aiai.ai.launchpad.repositories.SnippetBaseRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.utils.ZipUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;

@Controller
@RequestMapping("/launchpad")
@Slf4j
public class SnippetController {

    private final Globals globals;
    private final SnippetBaseRepository snippetBaseRepository;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;

    @Data
    public static class Result {
        Iterable<SnippetBase> snippets;
    }

    public SnippetController(Globals globals, SnippetBaseRepository snippetBaseRepository, SnippetRepository snippetRepository, SnippetService snippetService) {
        this.globals = globals;
        this.snippetBaseRepository = snippetBaseRepository;
        this.snippetRepository = snippetRepository;
        this.snippetService = snippetService;
    }

    @GetMapping("/snippets")
    public String init(@ModelAttribute Result result, @ModelAttribute("errorMessage") final String errorMessage) {
        result.snippets = snippetBaseRepository.findAll();
        return "launchpad/snippets";
    }

    @PostMapping(value = "/snippet-upload-from-file")
    public String uploadSnippet(MultipartFile file, final RedirectAttributes redirectAttributes) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#422.01 name of uploaded file is null");
            return "redirect:/launchpad/snippets";
        }
        int idx;
        if ((idx = originFilename.lastIndexOf('.')) == -1) {
            redirectAttributes.addFlashAttribute("errorMessage", "#422.02 '.' wasn't found, bad filename: " + originFilename);
            return "redirect:/launchpad/snippets";
        }
        String ext = originFilename.substring(idx).toLowerCase();
        if (!".zip".equals(ext)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#422.03 only '.zip' files is supported, filename: " + originFilename);
            return "redirect:/launchpad/snippets";
        }

        final String location = System.getProperty("java.io.tmpdir");

        try {
            File tempDir = DirUtils.createTempDir("snippet-upload-");
            if (tempDir==null || tempDir.isFile()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#422.04 can't create temporary directory in " + location);
                return "redirect:/launchpad/snippets";
            }
            final File zipFile = new File(tempDir, "snippet.zip");
            FileUtils.copyInputStreamToFile(file.getInputStream(), zipFile);
            ZipUtils.unzipFolder(zipFile, tempDir);
            snippetService.loadSnippetsRecursively(tempDir);
        }
        catch (Exception e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#422.05 can't load snippets, Error: " + e.toString());
            return "redirect:/launchpad/snippets";
        }

        return "redirect:/launchpad/snippets";
    }
}
