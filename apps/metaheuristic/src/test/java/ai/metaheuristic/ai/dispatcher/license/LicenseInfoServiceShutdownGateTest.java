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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shutdown gate of {@link LicenseInfoService}, Spring-less.
 *
 * <p>The subject is built by hand with nulls for every collaborator. Once shut down, neither entry
 * point may reach any of them, so a missing or misplaced gate surfaces as a NullPointerException
 * instead of a green test. Each {@code @Test} owns its own instance, so the class is safe to run
 * concurrently.
 *
 * <p>❗ The not-shutdown path lives in {@link LicenseInfoServiceStatusResultTest}, against the real bean.
 * The real singleton of the shared Spring context is never shut down by any test — that would make it
 * refuse every later call in the same JVM.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class LicenseInfoServiceShutdownGateTest {

    private static LicenseInfoService newService() {
        return new LicenseInfoService(null, null, null, null, null);
    }

    @Test
    public void test_freshService_isNotShutdown() {
        assertFalse(newService().isShutdown());
    }

    @Test
    public void test_info_afterShutdown_returnsShutdownErrorAndNoInfo() {
        final LicenseInfoService service = newService();
        service.shutdown();
        assertTrue(service.isShutdown());

        final LicenseInfoData.LicenseStatusResult result = service.info();

        assertNull(result.info, "info() must not produce a LicenseInfo after shutdown");
        assertEquals(List.of("01.257.020 Shutdown in progress"), result.errorMessages);
    }

    @Test
    public void test_capabilities_afterShutdown_returnsShutdownErrorAndNoInfo() {
        final LicenseInfoService service = newService();
        service.shutdown();
        assertTrue(service.isShutdown());

        final LicenseInfoData.CapabilitiesResult result = service.capabilities();

        assertNull(result.info, "capabilities() must not produce Capabilities after shutdown");
        assertEquals(List.of("01.257.030 Shutdown in progress"), result.errorMessages);
    }

    @Test
    public void test_shutdown_isPerInstance() {
        final LicenseInfoService stopped = newService();
        final LicenseInfoService other = newService();
        stopped.shutdown();

        assertTrue(stopped.isShutdown());
        assertFalse(other.isShutdown());
    }
}
