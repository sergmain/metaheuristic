/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.license.LicenseArtifactService;
import ai.metaheuristic.ai.dispatcher.license.LicenseInfoData;
import ai.metaheuristic.ai.dispatcher.license.LicenseInfoService;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * License status and management for the MainAdmin.
 *
 * <p>Requires a signed-file backend, which today is every runnable one — {@code internal-lm} and
 * the two test harnesses all supply a {@code SignedFileLicenseSource}. It is deliberately NOT
 * enumerating them: a list of backend profiles here would have to be edited every time one is
 * added, which is the negative-list problem wearing a positive hat.
 *
 * <p>⚠️ When {@code aws-lm} is actually wired, these endpoints must stop existing under it — AWS is
 * the authority there and there is nothing to install. That is the moment to introduce a marker
 * profile for the signed-file family (e.g. via {@code spring.profiles.group}), with a real second
 * family member to design against rather than a guess.
 *
 * <p>Install is ADD, never replace — an installation holds a SET of licenses, and a trial that
 * cannot be extended is continued by installing a second license beside the first. DELETE exists
 * because an offline license cannot be revoked: removing the artifact from a machine you control
 * is the only way to retire one.
 *
 * @author Serge
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/license")
@Slf4j
@Profile("dispatcher")
@ConditionalOnBean(SignedFileLicenseSource.class)
@CrossOrigin
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseRestController {

    private final LicenseInfoService licenseInfoService;
    private final LicenseArtifactService licenseArtifactService;
    private final UserContextService userContextService;

    /** The effective entitlement plus the per-license breakdown. */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public LicenseInfoData.LicenseStatusResult status() {
        return new LicenseInfoData.LicenseStatusResult(licenseInfoService.info());
    }

    /**
     * The effective capability list, for ANY authenticated user.
     *
     * <p>Separate from /status on purpose: /status is MainAdmin-only because it names the licensee,
     * the installation id and every installed licence. This returns only what the UI needs to avoid
     * offering a user a feature that will be refused — which the UI reveals anyway by which parts
     * work, so there is nothing to withhold.
     *
     * <p>❗ UX, never enforcement. The gates are the boundary; a browser can ask for anything.
     */
    @GetMapping("/capabilities")
    @PreAuthorize("isAuthenticated()")
    public LicenseInfoData.CapabilitiesResult capabilities() {
        return new LicenseInfoData.CapabilitiesResult(licenseInfoService.capabilities());
    }

    /**
     * Add ONE license to the set. The token is verified before anything is written, so a rejected
     * upload leaves the existing set untouched.
     */
    @PostMapping("/install")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest install(@RequestParam("token") @Nullable String token, Authentication authentication) {
        final UserContext ctx = userContextService.getContext(authentication);
        return licenseArtifactService.install(token, ctx.getAccountId());
    }

    /** Remove ONE license from the set. Flips IS_DELETED; the row and its audit trail survive. */
    @DeleteMapping("/{artifactId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest remove(@PathVariable Long artifactId) {
        return licenseArtifactService.remove(artifactId);
    }
}
