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
import aiai.ai.launchpad.beans.DatasetGroup;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.dataset.DatasetUtils;
import aiai.ai.yaml.snippet.SnippetVersion;
import aiai.ai.launchpad.repositories.DatasetGroupsRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.utils.checksum.Checksum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("/payload")
@Slf4j
public class PayloadController {

    private static final HttpEntity<byte[]> EMPTY_HTTP_ENTITY = new HttpEntity<>(new byte[0], getHeader(0));
    private static final HttpEntity<String> EMPTY_STRING_HTTP_ENTITY = new HttpEntity<>("", getHeader(0));
    private  final SnippetRepository snippetRepository;
    private  final DatasetGroupsRepository datasetGroupsRepository;

    private final Globals globals;

    public PayloadController(SnippetRepository snippetRepository, DatasetGroupsRepository datasetGroupsRepository, Globals globals) {
        this.snippetRepository = snippetRepository;
        this.datasetGroupsRepository = datasetGroupsRepository;
        this.globals = globals;
    }

    @GetMapping("/dataset/{id}")
    public HttpEntity<PathResource> datasets(@PathVariable("id") long datasetId) {

        final File datasetFile = DatasetUtils.getDatasetFile(globals.launchpadDir, datasetId);

        return new HttpEntity<>(new PathResource(datasetFile.toPath()), getHeader(datasetFile.length()) );
    }

    @GetMapping("/feature/{featureId}")
    public HttpEntity<AbstractResource> feature(HttpServletResponse response, @PathVariable("featureId") long featureId) throws IOException {

        DatasetGroup datasetGroup = datasetGroupsRepository.findById(featureId).orElse(null);
        if (datasetGroup==null) {
            log.info("Feature wan't found for id {}", featureId);
            response.sendError(HttpServletResponse.SC_GONE);
            return new HttpEntity<>(new ByteArrayResource(new byte[0]), getHeader(0));
        }
        final File featureFile = DatasetUtils.getFeatureFile(globals.launchpadDir, datasetGroup.getDataset().getId(), featureId);

        return new HttpEntity<>(new PathResource(featureFile.toPath()), getHeader(featureFile.length()));
    }

    @GetMapping("/snippet/{name}")
    public HttpEntity<byte[]> snippets(HttpServletResponse response, @PathVariable("name") String snippetName) throws IOException {

        SnippetVersion snippetVersion = SnippetVersion.from(snippetName);
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            log.info("Snippet wan't found for name {}", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_HTTP_ENTITY;
        }

        if (snippet.getChecksum()==null) {
            log.info("Checksum for snippet {} wan't found", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_HTTP_ENTITY;
        }

        Checksum checksum = Checksum.fromJson(snippet.getChecksum());
        for (Map.Entry<Checksum.Type, String> entry : checksum.checksums.entrySet()) {
            String sum = entry.getKey().getChecksum( snippet.getCode() );
            if (sum.equals(entry.getValue())) {
                log.info("Snippet {}, checksum is Ok", snippet.getSnippetCode());
            }
            else {
                log.error("Snippet {}, checksum is wrong, expected: {}, actual: {}", snippet.getSnippetCode(), entry.getValue(), sum );
                response.sendError(HttpServletResponse.SC_CONFLICT);
                return EMPTY_HTTP_ENTITY;
            }
        }

        final int length = snippet.getCode().length;
        log.info("Send snippet, length: {}", length);

        return new HttpEntity<>(snippet.getCode(), getHeader(length) );
    }

    @GetMapping("/snippet-checksum/{name}")
    public HttpEntity<String> snippetChecksum(HttpServletResponse response, @PathVariable("name") String snippetName) throws IOException {

        SnippetVersion snippetVersion = SnippetVersion.from(snippetName);
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            log.info("Snippet wan't found for name {}", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_STRING_HTTP_ENTITY;
        }

        if (snippet.getChecksum()==null) {
            log.info("Checksum for snippet {} wan't found", snippetName);
            response.sendError(HttpServletResponse.SC_GONE);
            return EMPTY_STRING_HTTP_ENTITY;
        }

        Checksum checksum = Checksum.fromJson(snippet.getChecksum());
        for (Map.Entry<Checksum.Type, String> entry : checksum.checksums.entrySet()) {
            String sum = entry.getKey().getChecksum( snippet.getCode() );
            if (sum.equals(entry.getValue())) {
                log.info("Snippet {}, checksum is Ok", snippet.getSnippetCode());
            }
            else {
                log.error("Snippet {}, checksum is wrong, expected: {}, actual: {}", snippet.getSnippetCode(), entry.getValue(), sum );
                response.sendError(HttpServletResponse.SC_CONFLICT);
                return EMPTY_STRING_HTTP_ENTITY;
            }
        }

        final int length = snippet.getCode().length;
        log.info("Send snippet checksum, length: {}", snippet.getSnippetCode(), length);

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
