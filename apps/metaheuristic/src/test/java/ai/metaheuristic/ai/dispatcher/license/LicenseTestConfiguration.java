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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The licence backend the MH test suite runs under, selected by the {@code mh-test-lm} profile.
 *
 * <p>One {@code *-lm} profile is active per context and each declares itself positively, so this
 * is not an override of {@link LicenseConfiguration} — {@code internal-lm} is simply not active in
 * tests. Adding a backend never edits an existing annotation.
 *
 * <p>❗ This licence grants {@code Capability:BATCH} and nothing else. The proprietary side runs
 * under its own profile, its own key and its own licence, because MH must not name proprietary
 * capabilities even in a test fixture.
 *
 * <p><b>Everything here is in the test source set and nothing ships.</b> That is the whole point:
 * the production verification key is a compiled-in constant so a customer cannot swap it, and a
 * class under {@code src/main} reading a key from configuration would hand that guarantee away —
 * Spring does not care whether a value arrives from a properties file, a command-line argument or
 * an environment variable. Because this reader exists only on the test classpath, activating the
 * {@code test} profile against a shipped build gains nobody anything: there is no such class in
 * the jar.
 *
 * @author Serge
 */
@Configuration
@Profile("dispatcher & mh-test-lm")
@Slf4j
public class LicenseTestConfiguration {

    @Bean
    public SignedFileLicenseSource licenseSource(
            Globals globals,
            LicenseInstallationService licenseInstallationService,
            DeploymentValuesResolverHolder deploymentValuesResolverHolder,
            @Value("${mh.test-license.private-key}") String privateKeyBase64,
            @Value("${mh.test-license.public-key}") String publicKeyBase64) {

        final ECPrivateKey priv = LicenseTestFixture.privateKey(privateKeyBase64);
        final ECPublicKey pub = LicenseTestFixture.publicKey(publicKeyBase64);
        final String token = LicenseTestFixture.mintSuiteLicense(priv, Instant.now(Clock.systemUTC()));

        log.info("Test licence minted for this run: BATCH only, H2 only, valid for {}", LicenseTestFixture.TTL);

        return new SignedFileLicenseSource(
                () -> List.of(token),
                LicenseTestFixture.keyResolver(pub),
                () -> Instant.now(Clock.systemUTC()),
                deploymentValuesResolverHolder::current,
                licenseInstallationService::installationId,
                globals.license.cacheTtl);
    }
}
