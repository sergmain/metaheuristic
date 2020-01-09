/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.server;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author Serge
 * Date: 1/8/2020
 * Time: 12:20 AM
 */
@RestController
@Slf4j
@Profile("launchpad")
@RequestMapping("/rest/v1/asset")
@PreAuthorize("hasAnyRole('ASSET_REST_ACCESS')")
@RequiredArgsConstructor
public class AssetController {

    private final Globals globals;
    private final ServerService serverService;
    private final SnippetService snippetService;

    @GetMapping(value="/snippet/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverResourceAuth(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @SuppressWarnings("unused") String stationId,
            @SuppressWarnings("unused") Long taskId,
            String code, String chunkSize, Integer chunkNum) {
        String normalCode = new File(code).getName();
        log.debug("deliverResourceAuth(), code: {}, chunkSize: {}, chunkNum: {}", normalCode, chunkSize, chunkNum);
        if (chunkSize==null || chunkSize.isBlank() || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            ResourceWithCleanerInfo resource = serverService.deliverResource(EnumsApi.BinaryDataType.SNIPPET, normalCode, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (BinaryDataNotFoundException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @PostMapping("/snippet-checksum/{random-part}")
    public String snippetChecksumAuth(
            HttpServletResponse response,
            @SuppressWarnings("unused") String stationId,
            @SuppressWarnings("unused") String taskId,
            String code,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) throws IOException {
        return getSnippetChecksum(response, code);
    }

    private String getSnippetChecksum(HttpServletResponse response, String snippetCode) throws IOException {
        Snippet snippet = snippetService.findByCode(snippetCode);
        if (snippet==null) {
            log.warn("#440.100 Snippet {} wasn't", snippetCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return null;
        }
        SnippetConfigYaml sc = snippet.getSnippetConfig(false);
        log.info("#440.120 Send checksum {} for snippet {}", sc.checksum, sc.getCode());
        return sc.checksum;
    }

    @PostMapping("/snippet-config/{random-part}")
    public String snippetConfig(
            HttpServletResponse response,
            @SuppressWarnings("unused") String stationId,
            @SuppressWarnings("unused") String taskId,
            String code,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) throws IOException {
        return getSnippetConfig(response, code);
    }

    private String getSnippetConfig(HttpServletResponse response, String snippetCode) throws IOException {
        Snippet snippet = snippetService.findByCode(snippetCode);
        if (snippet==null) {
            log.warn("#440.140 Snippet {} wasn't found", snippetCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return null;
        }
        SnippetConfigYaml sc = snippet.getSnippetConfig(false);
        log.info("Send snippet config for snippet {}", sc.getCode());
        return SnippetConfigYamlUtils.BASE_YAML_UTILS.toString(sc);
    }


}
