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
import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.api.ApiTxService;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 10:38 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/api")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ApiRestController {

    private final ApiService apiService;
    private final ApiTxService apiTxService;
    private final UserContextService userContextService;

    @GetMapping("/apis")
    public ApiData.Apis apis(Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ApiData.Apis apis = apiService.getApis(pageable, context);
        return apis;
    }

    @GetMapping("/api/{apiId}")
    public ApiData.Api apis(@PathVariable @Nullable Long apiId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ApiData.Api api = apiService.getApiAsData(apiId, context);
        return api;
    }

    @PostMapping("/api-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addFormCommit(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "code") String code,
            @RequestParam(name = "scheme") String scheme,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);

        return apiTxService.createApi(name, code, scheme, context);
    }
/*
    @PostMapping("/evaluation-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult addFormCommit(@RequestParam(name = "source") String sourceCodeYamlAsStr, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeService.createSourceCode(sourceCodeYamlAsStr, context.getCompanyId());
    }

    @PostMapping("/evaluation-edit-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult editFormCommit(Long sourceCodeId, @RequestParam(name = "source") String sourceCodeYamlAsStr) {
        throw new IllegalStateException("Not supported any more");
    }
*/

    @PostMapping("/api-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return apiTxService.deleteApiById(id, context);
    }

}
