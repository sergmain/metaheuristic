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
 * <p>❗ <b>A licence-manager profile is REQUIRED.</b> Every {@code *-lm} backend declares itself
 * POSITIVELY, exactly as the storage backends do — {@code internal-lm} here, {@code aws-lm} for
 * AWS, and {@code mh-test-lm} for this module's test harness. A dispatcher started
 * with none of them has no {@code LicenseSource} and FAILS TO START. That is the point: running
 * unlicensed is not a state this application has, and a licence you can switch off by deleting a
 * profile name is not a licence.
 *
 * <p>❗ The expression is positive on purpose. An earlier attempt used {@code !aws-lm & !test},
 * which made every new backend an edit to every existing annotation and buried the list of what
 * exists inside a pile of negations. Adding a backend must be additive: declare its own profile,
 * add its own configuration class, touch nothing here.
 *
 * <p>The bean is typed {@code SignedFileLicenseSource} rather than {@code LicenseSource} so the
 * admin projection can reach {@code currentResult()} for the per-license breakdown. Gates inject
 * the interface and stay backend-blind; only the status page, which exists to report which backend
 * is active, knows the difference.
 *
 * @author Serge
 */
@Configuration
@Profile("dispatcher & internal-lm")
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
