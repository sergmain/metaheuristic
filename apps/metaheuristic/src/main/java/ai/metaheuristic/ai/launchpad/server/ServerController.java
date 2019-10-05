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

package ai.metaheuristic.ai.launchpad.server;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.api.data.SnippetApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@RestController
@Slf4j
@Profile("launchpad")
@RequestMapping("/rest/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'SERVER_REST_ACCESS')")
@RequiredArgsConstructor
public class ServerController {

    private final Globals globals;
    private final ServerService serverService;
    private final SnippetService snippetService;

    @PostMapping("/registry")
    public RegistryData getRegistryData() {
        return null;
        //
    }

    @PostMapping("/srv/{random-part}")
    public String processRequestAuth(@SuppressWarnings("unused") @PathVariable("random-part") String randomPart,@RequestBody String data) {
        log.debug("processRequestAuth(), globals.isSecurityEnabled: {}, data: {}", globals.isSecurityEnabled, data);
        return "{}";
    }

    @PostMapping("/srv-v2/{random-part}")
    public String processRequestAuth(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @RequestBody String data
            ) {
        log.debug("processRequestAuth(), globals.isSecurityEnabled: {}, data: {}", globals.isSecurityEnabled, data);
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @GetMapping(value="/payload/resource/{type}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverResourceAuth(
            HttpServletRequest request,
            @PathVariable("type") String typeAsStr,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @SuppressWarnings("unused") String stationId,
            @SuppressWarnings("unused") Long taskId,
            String code, String chunkSize, Integer chunkNum) {
        String normalCode = new File(code).getName();
        log.debug("deliverResourceAuth(), globals.isSecurityEnabled: {}, typeAsStr: {}, code: {}, chunkSize: {}, chunkNum: {}",
                globals.isSecurityEnabled, typeAsStr, normalCode, chunkSize, chunkNum);
        if (chunkSize==null || chunkSize.isBlank() || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            ResourceWithCleanerInfo resource = serverService.deliverResource(typeAsStr, normalCode, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (BinaryDataNotFoundException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @PostMapping("/upload/{random-part}")
    public UploadResult uploadResourceAuth(
            MultipartFile file,
            @SuppressWarnings("unused") String stationId,
            Long taskId,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) {
        log.debug("uploadResourceAuth(), globals.isSecurityEnabled: {}, taskId: {}", globals.isSecurityEnabled, taskId);
        return serverService.uploadResource(file, taskId);
    }

    @PostMapping("/payload/snippet-checksum/{random-part}")
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
            log.warn("#440.100 Snippet wasn't found for code {}", snippetCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return null;
        }
        SnippetApiData.SnippetConfig sc = snippet.getSnippetConfig();
        log.info("Send checksum {} for snippet {}", sc.checksum, sc.getCode());
        return sc.checksum;
    }

    /**
     * This endpoint is only for testing security. Do not delete
     * @return String
     */
    @GetMapping("/test")
    public String getMessage_2() {
        return "Ok";
    }

}
