/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableTopLevelService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
// !!! __ Do not change the name of class to SouthBridgeController ___ !!!
public class SouthbridgeController {

    private final SouthbridgeService serverService;
    private final ExecContextVariableTopLevelService execContextVariableTopLevelService;

    @PostMapping("/srv-v2/{random-part}")
    public String processRequestWithAuth(
            HttpServletRequest request, HttpServletResponse response,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @Nullable @RequestBody String data
            ) throws IOException {
        log.debug("processRequestAuth(), data: {}", data);
        if (S.b(data)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return "";
        }
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @PostMapping("/keep-alive/{random-part}")
    public String keepAlive(
            HttpServletRequest request, HttpServletResponse response,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @Nullable @RequestBody String data
    ) throws IOException {
        log.debug("keepAlive(), data: {}", data);
        if (S.b(data)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return "";
        }
        return serverService.keepAlive(data, request.getRemoteAddr());
    }

    @GetMapping(value="/payload/resource/{variableType}/{taskId}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverResourceAuth(
            HttpServletRequest request,
            @PathVariable("variableType") String variableType,
            @PathVariable("taskId") Long taskId,
            @SuppressWarnings("unused") @Nullable @PathVariable("random-part") String randomPart,
            @Nullable String id, @Nullable String chunkSize, @Nullable Integer chunkNum) {
        log.debug("deliverResourceAuth(), id: {}, chunkSize: {}, chunkNum: {}", id, chunkSize, chunkNum);
        if (S.b(id) || S.b(chunkSize) || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = serverService.deliverData(taskId, EnumsApi.DataType.valueOf(variableType), id, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @GetMapping(value="/payload/resource/{variableType}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverResourceAuthOld(
            HttpServletRequest request,
            @PathVariable("variableType") String variableType,
            @SuppressWarnings("unused") @Nullable @PathVariable("random-part") String randomPart,
            @Nullable String id, @Nullable String chunkSize, @Nullable Integer chunkNum) {
        log.debug("deliverResourceAuth(), id: {}, chunkSize: {}, chunkNum: {}", id, chunkSize, chunkNum);
        if (S.b(id) || S.b(chunkSize) || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = serverService.deliverData(null, EnumsApi.DataType.valueOf(variableType), id, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @PostMapping("/upload/{random-part}")
    public UploadResult uploadVariable(
            @Nullable MultipartFile file,
            @SuppressWarnings("unused") @Nullable String processorId,
            @Nullable Long taskId,
            @Nullable Long variableId,
            @Nullable Boolean nullified,
            @SuppressWarnings("unused") @Nullable @PathVariable("random-part") String randomPart
    ) {
        log.debug("uploadVariable(), taskId: #{}, variableId: {}", taskId, variableId);
        if (Boolean.TRUE.equals(nullified)) {
            return execContextVariableTopLevelService.setVariableAsNull(taskId, variableId);
        }
        else {
            return execContextVariableTopLevelService.uploadVariable(file, taskId, variableId);
        }
    }

    /**
     * return string as "true"/"false".
     *      "true" - if variable already inited or it doesn't exist
     *      "false" - variable isn't inited yet
     *
     * @param variableId
     * @return
     */
    @PostMapping("/variable-status")
    public String variableStatus(Long variableId) {
        log.debug("variableStatus(), variableId: {}", variableId);
        return execContextVariableTopLevelService.getVariableStatus(variableId);
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
