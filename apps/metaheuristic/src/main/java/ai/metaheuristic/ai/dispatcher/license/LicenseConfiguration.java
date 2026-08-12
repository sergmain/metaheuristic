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

package ai.metaheuristic.ai.dispatcher.license;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;

/**
 * The whole Spring surface of the offline license backend: four suppliers and one bean.
 *
 * <p>The verify core is Spring-less by construction, so this class owns no verification logic of
 * its own — it supplies where the tokens come from, what time it is, which deployment we are on,
 * and who we are. Everything else lives in {@code ai.metaheuristic.commons.spi.license}.
 *
 * <p>❗ <b>The internal backend is the DEFAULT for every dispatcher, not an opt-in.</b> The profile
 * expression is {@code !aws-lm} rather than {@code internal-lm} on purpose: an opt-in profile makes
 * the entire licence bypassable by deleting one name from a properties file, which is not a
 * licensing system. Deleting a profile name now does nothing; the only way to reach a dispatcher
 * with no {@code LicenseSource} is to activate {@code aws-lm}, which is reserved and unwired, and
 * such a dispatcher fails to start rather than running unlicensed. That is the honest outcome —
 * that backend genuinely is not wired.
 *
 * <p>{@code !test} excludes this class under the test profile, where the test source set supplies
 * its own backend with its own key. Nothing that reads a key from configuration ships.
 *
 * <p>The bean is typed {@code SignedFileLicenseSource} rather than {@code LicenseSource} so the
 * admin projection can reach {@code currentResult()} for the per-license breakdown. Gates inject
 * the interface and stay backend-blind; only the status page, which exists to report which backend
 * is active, knows the difference.
 *
 * @author Serge
 */
@Configuration
@Profile("dispatcher & !aws-lm & !test")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseConfiguration {

    private final Globals globals;
    private final LicenseTokenSupplier licenseTokenSupplier;
    private final LicenseInstallationService licenseInstallationService;
    private final DeploymentValuesResolverHolder deploymentValuesResolverHolder;

    @Bean
    public SignedFileLicenseSource licenseSource() {
        log.info("Initializing the internal (offline signed-file) license manager, dir: {}, cache TTL: {}",
                licenseTokenSupplier.licenseDir(), globals.license.cacheTtl);
        return SignedFileLicenseSource.withEmbeddedKey(
                licenseTokenSupplier::tokens,
                () -> Instant.now(Clock.systemUTC()),
                deploymentValuesResolverHolder::current,
                licenseInstallationService::installationId,
                globals.license.cacheTtl);
    }
}
