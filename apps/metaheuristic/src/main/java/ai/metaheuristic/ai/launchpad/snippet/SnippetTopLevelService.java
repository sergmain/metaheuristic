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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.SnippetData;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
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

import static ai.metaheuristic.ai.Consts.*;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class SnippetTopLevelService {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;
    private final SnippetService snippetService;
    private final BinaryDataService binaryDataService;

    public SnippetData.SnippetsResult getSnippets() {
        SnippetData.SnippetsResult result = new SnippetData.SnippetsResult();
        result.snippets = snippetRepository.findAll();
        result.snippets.sort((o1,o2)->o2.getId().compareTo(o1.getId()));
        result.assetMode = globals.assetMode;
        return result;
    }

    public OperationStatusRest deleteSnippetById(Long id) {
        log.info("Start deleting snippet with id: {}", id );
        if (globals.assetMode== Enums.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.005 Can't delete snippet while 'replicated' mode of asset is active");
        }
        final Snippet snippet = snippetCache.findById(id);
        if (snippet == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.010 snippet wasn't found, planId: " + id);
        }
        snippetCache.delete(snippet.getId());
        binaryDataService.deleteByCodeAndDataType(snippet.getCode(), EnumsApi.BinaryDataType.SNIPPET);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadSnippet(final MultipartFile file) {
        if (globals.assetMode==Enums.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.020 Can't upload snippet while 'replicated' mode of asset is active");
        }
        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.030 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.040 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.050 only '.zip', '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        File tempDir = null;
        try {
            tempDir = DirUtils.createTempDir("snippet-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#424.060 can't create temporary directory in " + location);
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
                    "#424.070 can't load snippets, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
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
