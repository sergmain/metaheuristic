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

package aiai.ai.launchpad;

import aiai.ai.Globals;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.beans.Dataset;
import aiai.ai.launchpad.beans.DatasetGroup;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.dataset.DatasetCache;
import aiai.ai.launchpad.dataset.DatasetUtils;
import aiai.ai.launchpad.repositories.DatasetGroupsRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.utils.checksum.CheckSumAndSignatureStatus;
import aiai.ai.utils.checksum.ChecksumWithSignatureService;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

@Controller
@RequestMapping("/payload")
@Slf4j
public class PayloadController {

    private static final HttpEntity<byte[]> EMPTY_HTTP_ENTITY = new HttpEntity<>(new byte[0], getHeader(0));
    private static final HttpEntity<String> EMPTY_STRING_HTTP_ENTITY = new HttpEntity<>("", getHeader(0));

    private final ChecksumWithSignatureService checksumWithSignatureService;
    private final SnippetRepository snippetRepository;
    private final DatasetGroupsRepository datasetGroupsRepository;
    private final BinaryDataService binaryDataService;
    private final DatasetCache datasetCache;

    private final Globals globals;

    public PayloadController(SnippetRepository snippetRepository, DatasetGroupsRepository datasetGroupsRepository, ChecksumWithSignatureService checksumWithSignatureService, BinaryDataService binaryDataService, DatasetCache datasetCache, Globals globals) {
        this.snippetRepository = snippetRepository;
        this.datasetGroupsRepository = datasetGroupsRepository;
        this.checksumWithSignatureService = checksumWithSignatureService;
        this.binaryDataService = binaryDataService;
        this.datasetCache = datasetCache;
        this.globals = globals;
    }

    @GetMapping("/dataset/{id}")
    @Transactional
    public HttpEntity<AbstractResource> datasets(HttpServletResponse response, @PathVariable("id") long datasetId) throws IOException {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset==null) {
            return returnEmptyAsGone(response);
        }
        File datasetFile = new File(globals.launchpadDir, dataset.asDatasetFilePath());
        if (globals.isStoreDataToDb()) {

            if (dataset.getLength()==null || dataset.getLength()==0) {
                log.error("dataset length isn't initialized, length: {}", dataset.getLength());
                return returnEmptyAsGone(response);
            }
            boolean isStore = false;
            if (!datasetFile.exists()) {
                isStore = true;
            }
            else if (datasetFile.length()!=dataset.getLength()) {
                datasetFile.delete();
                isStore = true;
            }

            if (isStore) {
                try {
                    binaryDataService.storeToFile(datasetId, BinaryData.Type.DATASET, datasetFile);
                } catch (BinaryDataNotFoundException e) {
                    return returnEmptyAsGone(response);
                }
            }
        }
        return new HttpEntity<>(new FileSystemResource(datasetFile.toPath()), getHeader(datasetFile.length()));
    }

    private HttpEntity<AbstractResource> returnEmptyAsGone(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_GONE);
        return new HttpEntity<>(new ByteArrayResource(new byte[0]), getHeader(0));
    }

    @GetMapping("/feature/{featureId}")
    @Transactional
    public HttpEntity<AbstractResource> feature(HttpServletResponse response, @PathVariable("featureId") long featureId) throws IOException {
        final DatasetGroup datasetGroup = datasetGroupsRepository.findById(featureId).orElse(null);
        if (datasetGroup==null) {
            log.info("Feature wasn't found for id {}", featureId);
            return returnEmptyAsGone(response);
        }
        final File featureFile = new File(globals.launchpadDir, datasetGroup.asFeatureFilePath());

        if (globals.isStoreDataToDb()) {
            if (datasetGroup.getLength()==null || datasetGroup.getLength()==0) {
                log.error("feature length isn't initialized, length: {}", datasetGroup.getLength());
                return returnEmptyAsGone(response);
            }
            boolean isStore = false;
            if (!featureFile.exists()) {
                isStore = true;
            }
            else if (featureFile.length()!=datasetGroup.getLength()) {
                featureFile.delete();
                isStore = true;
            }

            if (isStore) {
                try {
                    binaryDataService.storeToFile(featureId, BinaryData.Type.FEATURE, featureFile);
                } catch (BinaryDataNotFoundException e) {
                    return returnEmptyAsGone(response);
                }
            }
        }
        return new HttpEntity<>(new FileSystemResource(featureFile.toPath()), getHeader(featureFile.length()));
    }

    @GetMapping("/snippet/{name}")
    public HttpEntity<byte[]> snippets(HttpServletResponse response, @PathVariable("name") String snippetName) throws IOException {

        SnippetVersion snippetVersion = SnippetVersion.from(snippetName);
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            log.info("Snippet wasn't found for name {}", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_HTTP_ENTITY;
        }

        if (snippet.getChecksum()==null) {
            log.info("Checksum for snippet {} wan't found", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_HTTP_ENTITY;
        }

        Checksum checksum = Checksum.fromJson(snippet.getChecksum());
        CheckSumAndSignatureStatus status = checksumWithSignatureService.verifyChecksumAndSignature(checksum, snippetName, new ByteArrayInputStream(snippet.getCode()), false );
        if (!status.isOk) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return EMPTY_HTTP_ENTITY;
        }

        final int length = snippet.getCodeLength();
        log.info("Send snippet, length: {}", length);

        return new HttpEntity<>(snippet.getCode(), getHeader(length) );
    }

    @GetMapping("/snippet-checksum/{name}")
    public HttpEntity<String> snippetChecksum(HttpServletResponse response, @PathVariable("name") String snippetName) throws IOException {

        SnippetVersion snippetVersion = SnippetVersion.from(snippetName);
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            log.info("Snippet wasn't found for name {}", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_STRING_HTTP_ENTITY;
        }

        if (snippet.getChecksum()==null) {
            log.info("Checksum for snippet {} wan't found", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_STRING_HTTP_ENTITY;
        }
        Checksum checksum = Checksum.fromJson(snippet.getChecksum());
        CheckSumAndSignatureStatus status = checksumWithSignatureService.verifyChecksumAndSignature(checksum, snippetName, new ByteArrayInputStream(snippet.getCode()), false );
        if (!status.isOk) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return EMPTY_STRING_HTTP_ENTITY;
        }

        final int length = snippet.getChecksum().length();
        log.info("Send checksum for snippet {}, length: {}", snippet.getSnippetCode(), length);

        return new HttpEntity<>(snippet.getChecksum(), getHeader(length) );
    }

    private static HttpHeaders getHeader(long length) {
        HttpHeaders header = new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }
}
