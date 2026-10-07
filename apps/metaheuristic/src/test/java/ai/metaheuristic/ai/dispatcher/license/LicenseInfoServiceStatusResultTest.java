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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.shutdown.ShutdownService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LicenseInfoService} on the V3 harness — real Spring context, real H2, the {@code mh-test-lm}
 * licence backend.
 *
 * <p>Covers what the Spring-less {@link LicenseInfoServiceShutdownGateTest} cannot: the not-shutdown
 * path, which needs the real collaborators, and the wiring — that Spring actually hands this bean to
 * {@link ShutdownService}, so the gate is reached on context close.
 *
 * <p>❗ Read-only against the shared singleton. No test here calls {@code shutdown()} on it: the bean
 * would refuse every later call for the rest of the JVM run.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class LicenseInfoServiceStatusResultTest extends MhSharedItTest {

    @Autowired private LicenseInfoService licenseInfoService;
    @Autowired private ShutdownService shutdownService;

    @Test
    public void test_info_notShutdown_returnsLicenseInfoAndNoErrors() {
        assertFalse(licenseInfoService.isShutdown());

        final LicenseInfoData.LicenseStatusResult result = licenseInfoService.info();

        assertFalse(result.isErrorMessages(), result.getErrorMessagesAsStr());
        assertNotNull(result.info);
        assertNotNull(result.info.effective());
        assertNotNull(result.info.licenses());
    }

    @Test
    public void test_capabilities_notShutdown_matchTheEffectiveEntitlementOfInfo() {
        assertFalse(licenseInfoService.isShutdown());

        final LicenseInfoData.CapabilitiesResult result = licenseInfoService.capabilities();
        final LicenseInfoData.LicenseStatusResult status = licenseInfoService.info();

        assertFalse(result.isErrorMessages(), result.getErrorMessagesAsStr());
        assertFalse(status.isErrorMessages(), status.getErrorMessagesAsStr());
        assertNotNull(result.info);
        assertNotNull(status.info);
        // both are projections of the same aggregate, so they must agree
        assertEquals(status.info.effective().valid(), result.info.valid());
        assertEquals(status.info.effective().capabilities(), result.info.capabilities());
    }

    @Test
    public void test_licenseInfoService_isCollectedByShutdownService() {
        assertTrue(shutdownService.shutdowns.contains(licenseInfoService),
                "LicenseInfoService must be among the ShutdownInterface beans that ShutdownService informs on close");
    }
}
