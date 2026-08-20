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
import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import ai.metaheuristic.api.EnumsApi;
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
 *
 * <p>UPDATE: the {@code @ConditionalOnBean} reasoning above is superseded. That annotation is
 * only ordering-safe inside auto-configuration; on a component-scanned bean it races the
 * configuration that declares the source. The open-set concern it was chosen for is now met by
 * naming the backend family once in {@link ai.metaheuristic.ai.Consts#SIGNED_FILE_LM_PROFILE}.
 *
 * <p>Error code prefix: {@code 01.265.} (unique to this class).
 *
 * <p>❗ Every endpoint here is wrapped in try/catch. Two reasons, both learned the hard way on the
 * licence page: what the business service RETURNED was invisible - a refusal was a value, not an
 * exception, so it reached the browser and left nothing in the log to grep for - and an exception
 * thrown below reached the client as a bare 500 with no code to look up. The try logs the returned
 * value; the catch turns a throw into the same error envelope every other endpoint uses.
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/license")
@Slf4j
@Profile(Consts.SIGNED_FILE_LM_PROFILE)
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
        try {
            final LicenseInfoData.LicenseInfo info = licenseInfoService.info();
            if (log.isInfoEnabled()) {
                log.info("01.265.010 licenseInfoService.info() returned: {}", info);
            }
            return new LicenseInfoData.LicenseStatusResult(info);
        }
        catch (Throwable th) {
            log.error("01.265.015 error while reading the license status", th);
            // LicenseStatusResult extends BaseDataClass, so a failure travels in errorMessages
            // exactly as OperationStatusRest carries one; the endpoint's return type is fixed by
            // its contract and cannot become OperationStatusRest without breaking the caller.
            final LicenseInfoData.LicenseStatusResult result = new LicenseInfoData.LicenseStatusResult();
            result.addErrorMessage("01.265.020 error while reading the license status: " + th.getMessage());
            return result;
        }
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
        try {
            final LicenseInfoData.Capabilities capabilities = licenseInfoService.capabilities();
            if (log.isInfoEnabled()) {
                log.info("01.265.030 licenseInfoService.capabilities() returned: {}", capabilities);
            }
            return new LicenseInfoData.CapabilitiesResult(capabilities);
        }
        catch (Throwable th) {
            log.error("01.265.035 error while reading the license capabilities", th);
            final LicenseInfoData.CapabilitiesResult result = new LicenseInfoData.CapabilitiesResult();
            result.addErrorMessage("01.265.040 error while reading the license capabilities: " + th.getMessage());
            return result;
        }
    }

    /**
     * Add ONE license to the set. The token is verified before anything is written, so a rejected
     * upload leaves the existing set untouched.
     */
    @PostMapping("/install")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest install(@RequestParam("token") @Nullable String token, Authentication authentication) {
        try {
            final UserContext ctx = userContextService.getContext(authentication);
            final OperationStatusRest status = licenseArtifactService.install(token, ctx.getAccountId());
            // ❗ The token itself is never logged. A refusal is the interesting part and it is a
            // RETURN VALUE, which used to reach the browser and leave the server log silent - the
            // admin was shown a code that appeared nowhere in mh.log.
            if (log.isInfoEnabled() && status.infoMessages!=null) {
                log.info("01.265.050 licenseArtifactService.install() returned: {}, infoMessages: {}",
                        status.status, status.infoMessages);
            }
            if (log.isErrorEnabled() && status.errorMessages!=null) {
                log.error("01.265.052 licenseArtifactService.install() returned: {}, errorMessages: {}",
                        status.status, status.errorMessages);
            }
            return status;
        }
        catch (Throwable th) {
            log.error("01.265.055 error while installing a license", th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "01.265.060 error while installing a license: " + th.getMessage());
        }
    }

    /** Remove ONE license from the set. Flips IS_DELETED; the row and its audit trail survive. */
    @DeleteMapping("/{artifactId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest remove(@PathVariable Long artifactId) {
        try {
            final OperationStatusRest status = licenseArtifactService.remove(artifactId);
            if (log.isInfoEnabled() && status.infoMessages!=null) {
                log.info("01.265.070 licenseArtifactService.remove({}) returned: {}, infoMessages: {}",
                        artifactId, status.status, status.infoMessages);
            }
            if (log.isErrorEnabled() && status.errorMessages!=null) {
                log.error("01.265.072 licenseArtifactService.remove({}) returned: {}, errorMessages: {}",
                        artifactId, status.status, status.errorMessages);
            }
            return status;
        }
        catch (Throwable th) {
            log.error("01.265.075 error while removing the license with artifactId: " + artifactId, th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "01.265.080 error while removing the license with artifactId " + artifactId + ": " + th.getMessage());
        }
    }
}
