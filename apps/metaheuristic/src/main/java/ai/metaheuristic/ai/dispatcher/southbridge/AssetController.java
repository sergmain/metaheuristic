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
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author Serge
 * Date: 1/8/2020
 * Time: 12:20 AM
 */
@RestController
@Slf4j
@Profile("dispatcher")
@RequestMapping("/rest/v1/asset")
@PreAuthorize("hasAnyRole('ASSET_REST_ACCESS')")
@RequiredArgsConstructor
public class AssetController {

    private final SouthbridgeService serverService;
    private final FunctionTopLevelService functionTopLevelService;

    @GetMapping(value="/function/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverFunction(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @Nullable String code, @Nullable String chunkSize, @Nullable Integer chunkNum) {
        log.debug("deliverFunction(), code: {}, chunkSize: {}, chunkNum: {}", code, chunkSize, chunkNum);
        if (S.b(code) || S.b(chunkSize) || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = serverService.deliverData(EnumsApi.DataType.function, code, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @PostMapping("/function-checksum/{random-part}")
    public Map<EnumsApi.HashAlgo, String> functionChecksumAuth(
            HttpServletResponse response,
            @SuppressWarnings("unused") String processorId,
            @SuppressWarnings("unused") String taskId,
            @Nullable String code,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) throws IOException {
        if (S.b(code)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return Map.of();
        }
        return functionTopLevelService.getFunctionChecksum(response, code);
    }

    @PostMapping("/function-config/{random-part}")
    public String functionConfig(
            HttpServletResponse response,
            @SuppressWarnings("unused") String processorId,
            @SuppressWarnings("unused") String taskId,
            @Nullable String code,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) throws IOException {
        if (S.b(code)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return "";
        }
        return functionTopLevelService.getFunctionConfig(response, code);
    }
}
