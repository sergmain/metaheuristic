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

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.data.SnippetData;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@Service
@Slf4j
@Profile("launchpad")
public class SnippetTopLevelService {

    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;
    private final SnippetService snippetService;
    private final BinaryDataService binaryDataService;

    public SnippetTopLevelService(SnippetRepository snippetRepository, SnippetCache snippetCache, SnippetService snippetService, BinaryDataService binaryDataService) {
        this.snippetRepository = snippetRepository;
        this.snippetCache = snippetCache;
        this.snippetService = snippetService;
        this.binaryDataService = binaryDataService;
    }

    public SnippetData.SnippetsResult getSnippets() {
        SnippetData.SnippetsResult result = new SnippetData.SnippetsResult();
        result.snippets = snippetRepository.findAll();
        return result;
    }

    public OperationStatusRest deleteSnippetById(Long id) {
        log.info("Start deleting snippet with id: {}", id );
        final Snippet snippet = snippetCache.findById(id);
        if (snippet == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#422.50 snippet wasn't found, flowId: " + id);
        }
        snippetCache.delete(snippet.getId());
        binaryDataService.deleteByCodeAndDataType(snippet.getSnippetCode(), Enums.BinaryDataType.SNIPPET);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadSnippet(final MultipartFile file) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#422.01 name of uploaded file is null");
        }
        int idx;
        if ((idx = originFilename.lastIndexOf('.')) == -1) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#422.02 '.' wasn't found, bad filename: " + originFilename);
        }
        String ext = originFilename.substring(idx).toLowerCase();
        if (!".zip".equals(ext)) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#422.03 only '.zip' files is supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        try {
            File tempDir = DirUtils.createTempDir("snippet-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(Enums.OperationStatus.ERROR,
                        "#422.04 can't create temporary directory in " + location);
            }
            final File zipFile = new File(tempDir, "snippet.zip");
            log.debug("Start storing an uploaded snippet to disk");
            try(OutputStream os = new FileOutputStream(zipFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start unzipping archive");
            ZipUtils.unzipFolder(zipFile, tempDir);
            log.debug("Start loading snippet data to db");
            snippetService.loadSnippetsRecursively(tempDir);
        }
        catch (Exception e) {
            log.error("Error", e);
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#422.05 can't load snippets, Error: " + e.toString());
        }

        log.debug("All done. Send redirect");
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

}
