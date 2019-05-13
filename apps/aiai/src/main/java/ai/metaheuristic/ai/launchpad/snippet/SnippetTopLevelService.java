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

import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.SnippetData;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
public class SnippetTopLevelService {

    private static final String YAML_EXT = ".yaml";
    private static final String ZIP_EXT = ".zip";
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
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.50 snippet wasn't found, planId: " + id);
        }
        snippetCache.delete(snippet.getId());
        binaryDataService.deleteByCodeAndDataType(snippet.getCode(), EnumsApi.BinaryDataType.SNIPPET);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadSnippet(final MultipartFile file) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.01 name of uploaded file is null");
        }
        int idx;
        if ((idx = originFilename.lastIndexOf('.')) == -1) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.02 '.' wasn't found, bad filename: " + originFilename);
        }
        String ext = originFilename.substring(idx).toLowerCase();
        if (!StringUtils.equalsAny(ext, ZIP_EXT, YAML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.03 only '.zip' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        try {
            File tempDir = DirUtils.createTempDir("snippet-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#422.04 can't create temporary directory in " + location);
            }
            final File zipFile = new File(tempDir, "snippets" + ext);
            log.debug("Start storing an uploaded snippet to disk");
            try(OutputStream os = new FileOutputStream(zipFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            List<SnippetApiData.SnippetConfigStatus> statuses;
            if (ZIP_EXT.equals(ext)) {
                log.debug("Start unzipping archive");
                ZipUtils.unzipFolder(zipFile, tempDir);
                log.debug("Start loading snippet data to db");
                statuses = new ArrayList<>();
                snippetService.loadSnippetsRecursively(statuses, tempDir);
            }
            else {
                log.debug("Start loading snippet data to db");
                statuses = snippetService.loadSnippetsFromDir(tempDir);
            }
            if (isError(statuses)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        toErrorMessages(statuses));
            }
        }
        catch (Exception e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#422.05 can't load snippets, Error: " + e.toString());
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private List<String> toErrorMessages(List<SnippetApiData.SnippetConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).map(o->o.error).collect(Collectors.toList());
    }

    private boolean isError(List<SnippetApiData.SnippetConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).findFirst().orElse(null)!=null;
    }

}
