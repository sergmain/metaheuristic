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
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
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
import java.io.IOException;

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
    private final FunctionService functionService;

    @GetMapping(value="/function/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverFunctionAuth(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            String code, String chunkSize, Integer chunkNum) {
        log.debug("deliverFunctionAuth(), code: {}, chunkSize: {}, chunkNum: {}", code, chunkSize, chunkNum);
        if (chunkSize==null || chunkSize.isBlank() || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = serverService.deliverData(EnumsApi.DataType.function, code, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (BinaryDataNotFoundException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @PostMapping("/function-checksum/{random-part}")
    public String functionChecksumAuth(
            HttpServletResponse response,
            @SuppressWarnings("unused") String processorId,
            @SuppressWarnings("unused") String taskId,
            String code,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) throws IOException {
        return getFunctionChecksum(response, code);
    }

    private String getFunctionChecksum(HttpServletResponse response, String functionCode) throws IOException {
        Function function = functionService.findByCode(functionCode);
        if (function ==null) {
            log.warn("#440.100 Function {} wasn't", functionCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return "";
        }
        FunctionConfigYaml sc = function.getFunctionConfig(false);
        log.info("#440.120 Send checksum {} for function {}", sc.checksum, sc.getCode());
        return sc.checksum;
    }

    @PostMapping("/function-config/{random-part}")
    public String functionConfig(
            HttpServletResponse response,
            @SuppressWarnings("unused") String processorId,
            @SuppressWarnings("unused") String taskId,
            String code,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) throws IOException {
        return getFunctionConfig(response, code);
    }

    private String getFunctionConfig(HttpServletResponse response, String functionCode) throws IOException {
        Function function = functionService.findByCode(functionCode);
        if (function ==null) {
            log.warn("#440.140 Function {} wasn't found", functionCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return "";
        }
        FunctionConfigYaml sc = function.getFunctionConfig(false);
        log.info("Send config of function {}", sc.getCode());
        return FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);
    }
}
