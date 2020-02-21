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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.launchpad.data.FunctionData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.function.FunctionTopLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/launchpad/function")
@Profile("launchpad")
@CrossOrigin
@RequiredArgsConstructor
public class FunctionRestController {

    private final FunctionTopLevelService functionTopLevelService;

    @GetMapping("/functions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public FunctionData.FunctionsResult getFunctions() {
        return functionTopLevelService.getFunctions();
    }

    @GetMapping("/function-delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(@PathVariable Long id) {
        return functionTopLevelService.deleteFunctionById(id);
    }

    @PostMapping(value = "/function-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest uploadFunction(final MultipartFile file) {
        return functionTopLevelService.uploadFunction(file);
    }

}
