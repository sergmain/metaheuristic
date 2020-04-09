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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
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

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@RestController
@Slf4j
@Profile("dispatcher")
@RequestMapping("/rest/v1")
@PreAuthorize("hasAnyRole('SERVER_REST_ACCESS')")
@RequiredArgsConstructor
public class SouthbridgeController {

    private final SouthbridgeService serverService;

    @PostMapping("/srv-v2/{random-part}")
    public String processRequestAuth(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @RequestBody String data
            ) {
        log.debug("processRequestAuth(), data: {}", data);
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @GetMapping(value="/payload/resource/{variableType}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverResourceAuth(
            HttpServletRequest request,
            @PathVariable("variableType") String variableType,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            String id, String chunkSize, Integer chunkNum) {
        log.debug("deliverResourceAuth(), id: {}, chunkSize: {}, chunkNum: {}", id, chunkSize, chunkNum);
        if (chunkSize==null || chunkSize.isBlank() || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = serverService.deliverVariable(EnumsApi.BinaryType.valueOf(variableType), id, chunkSize, chunkNum);
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
            @SuppressWarnings("unused") String processorId,
            Long resourceId,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) {
        log.debug("uploadResourceAuth(), resourceId: {}", resourceId);
        return serverService.uploadVariable(file, resourceId);
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
