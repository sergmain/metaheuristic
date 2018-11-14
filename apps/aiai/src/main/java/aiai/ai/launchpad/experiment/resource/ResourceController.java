/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.launchpad.experiment.resource;

import aiai.ai.Globals;
import aiai.ai.core.ExecProcessService;
import aiai.ai.exceptions.StoreNewPartOfRawFileException;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
@Slf4j
@Profile("launchpad")
public class ResourceController {

    private static final Set<String> exts;

    @Data
    public static class ResourceDefinition {
        public String launchpadDirAsString;
        public boolean isAllPathsValid;

        public ResourceDefinition(String launchpadDirAsString) {
            this.launchpadDirAsString = launchpadDirAsString;
        }
    }

    static {
        exts = new HashSet<>();
        Collections.addAll(exts, ".json", ".csv", ".txt", ".xml", ".yaml");
    }

    private final Globals globals;
    private final ResourceService resourceService;
    private final SnippetService snippetService;
    private final SnippetCache snippetCache;
    private final EnvService envService;
    private final BinaryDataService binaryDataService;

    public ResourceController(Globals globals, ExecProcessService execProcessService, SnippetService snippetService, SnippetCache snippetCache, EnvService envService, BinaryDataService binaryDataService, ResourceService resourceService) {
        this.globals = globals;
        this.snippetService = snippetService;
        this.snippetCache = snippetCache;
        this.envService = envService;
        this.binaryDataService = binaryDataService;
        this.resourceService = resourceService;
    }

    private static final Random r = new Random();
    @PostMapping(value = "/resource-upload-from-file")
    public String createDefinitionFromFile(
            MultipartFile file,
            @RequestParam(name = "code") String resourceCode,
            @RequestParam(name = "poolCode") String resourcePoolCode,
            final RedirectAttributes redirectAttributes) {
        File tempFile = globals.createTempFileForLaunchpad("temp-raw-file-");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        try {
            FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#173.06 can't persist uploaded file as " +
                            tempFile.getAbsolutePath()+", error: " + e.toString());
            return "redirect:/launchpad/resources";
        }

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.01 name of uploaded file is null");
            return "redirect:/launchpad/resources";
        }
        if (!checkExtension(originFilename)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.03 not supported extension, filename: " + originFilename);
            return "redirect:/launchpad/resources";
        }


        try {
            resourceService.storeNewPartOfRawFile(originFilename, tempFile, true, resourceCode, resourcePoolCode);
        } catch (StoreNewPartOfRawFileException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#172.04 An error while saving data to file, " + e.toString());
            return "redirect:/launchpad/resources";
        }
        return "redirect:/launchpad/resources";
    }

    @PostMapping("/resource-delete-commit/{id}")
    public String deletePath(@PathVariable Long id) {
        if (true) throw new IllegalStateException("Not implemented yet");
        // TODO change code for using aiai_lp_data table
        return "redirect:/launchpad/resources";
    }

    private static boolean checkExtension(String filename) {
        int idx;
        if ((idx = filename.lastIndexOf('.')) == -1) {
            return false;
        }
        String ext = filename.substring(idx).toLowerCase();
        return exts.contains(ext);
    }
}
