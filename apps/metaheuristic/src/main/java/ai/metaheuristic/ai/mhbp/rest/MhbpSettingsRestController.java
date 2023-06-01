/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.rest;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.mhbp.settings.MhbpSettingsService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * @author Sergio Lissner
 * Date: 5/20/2023
 * Time: 10:36 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/mhbp-settings")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class MhbpSettingsRestController {

    private final MhbpSettingsService mhbpSettingsService;
    private final UserContextService userContextService;

    //  @RequestHeader("Content-Type") String contentType
    @PostMapping(value = "/import")
    //@PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest importBackup(final MultipartFile file, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest result = mhbpSettingsService.importBackup(file, context);
        return result;
    }

    // text/yaml
    // /rest/v1/dispatcher/mhbp-settings/export
    @GetMapping(value = "/export")
    //@PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public HttpEntity<String> exportBackup() {
        final String backup = mhbpSettingsService.exportBackup();

        HttpHeaders httpHeaders = new HttpHeaders();
//        httpHeaders.setContentType(new MediaType("application/text;charset=UTF-8"));
        httpHeaders.setContentType(new MediaType("application", "text", StandardCharsets.UTF_8));
        // https://stackoverflow.com/questions/93551/how-to-encode-the-filename-parameter-of-content-disposition-header-in-http
        // after adding 'attachment;' mh-angular must be fixed as well
        String filename = S.f("backup-%s.yaml", LocalDate.now().toString() );
        httpHeaders.setContentDisposition(ContentDisposition.parse(
                "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8)));

        HttpEntity<String> entity = new ResponseEntity<>(backup, httpHeaders, HttpStatus.OK);
        return entity;
    }
}
