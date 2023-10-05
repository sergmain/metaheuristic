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

package ai.metaheuristic.ai.dispatcher.bundle;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.BundleData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Serge
 * Date: 7/26/2021
 * Time: 10:46 PM
 */
@Controller
@RequestMapping("/dispatcher/bundle")
@Slf4j
@Profile("dispatcher")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
@RequiredArgsConstructor
public class BundleController {

    private static final String REDIRECT_BUNDLE_BUNDLE_ADD = "redirect:/dispatcher/bundle/bundle-add";

    private final BundleService bundleTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/bundlea-add")
    public String index() {
        return "dispatcher/bundle/bundle-add";
    }

    @PostMapping(value = "/bundle-upload-from-file")
    public String uploadFile(final MultipartFile file, Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        BundleData.UploadingStatus uploadingStatus = bundleTopLevelService.uploadFromFile(file, sourceCodeId, context);

        ControllerUtils.initRedirectAttributes(redirectAttributes, uploadingStatus);
        return REDIRECT_BUNDLE_BUNDLE_ADD;
    }

}
