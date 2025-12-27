/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.bundle.BundleService;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


/**
 * @author Serge
 * Date: 7/26/2021
 * Time: 10:46 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/bundle")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BundleRestController {

    private final BundleService bundleTopLevelService;
    private final UserContextService userContextService;

    @PostMapping(value = "/bundle-upload-from-file")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN')")
    public BundleData.UploadingStatus uploadFile(final MultipartFile file, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        BundleData.UploadingStatus status = bundleTopLevelService.uploadFromFile(file, context);
        return status;
    }

    @PostMapping(value = "/bundle-upload-from-git")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN')")
    public BundleData.UploadingStatus uploadFromGit(String repo, String branch, String commit, String path, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        GitInfo gitInfo = new GitInfo(repo, branch, commit, path);
        BundleData.UploadingStatus status = bundleTopLevelService.uploadFromGit(gitInfo, context);
        return status;
    }

}
