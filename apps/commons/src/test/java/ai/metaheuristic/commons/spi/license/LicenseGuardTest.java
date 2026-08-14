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

package ai.metaheuristic.commons.spi.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * How a gate behaves. Spring-less: a gate is a question put to an Entitlements snapshot, and a
 * hand-rolled snapshot answers it just as well as a verified one.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseGuardTest {

    private static final Feature ALPHA = new Feature("Cat.ALPHA");
    private static final Feature BETA = new Feature("Cat.BETA");

    /** Counts how often the gate consults the source, so caching can be ruled out. */
    private record CountingSource(AtomicReference<Entitlements> snapshot, AtomicInteger calls) implements LicenseSource {
        @Override
        public Entitlements current() {
            calls.incrementAndGet();
            return snapshot.get();
        }
    }

    private static Entitlements entitlements(LicenseState state, Set<String> keys) {
        return new ClaimsEntitlements(state, Instant.parse("2027-01-01T00:00:00Z"), keys);
    }

    @Test
    public void test_licensedCapability_passes() {
        final LicenseSource src = () -> entitlements(LicenseState.VALID, Set.of("Cat.ALPHA"));

        assertDoesNotThrow(() -> LicenseGuard.require(src, ALPHA));
    }

    @Test
    public void test_unlicensedCapability_throws() {
        final LicenseSource src = () -> entitlements(LicenseState.VALID, Set.of("Cat.ALPHA"));

        final LicenseException ex = assertThrows(LicenseException.class, () -> LicenseGuard.require(src, BETA));
        assertTrue(ex.getMessage().startsWith("01.263.010"), ex.getMessage());
        assertEquals("Cat.BETA", ex.featureName);
    }

    @Test
    public void test_invalidLicense_refusesEvenAGrantedCapability() {
        // has(f) is false whenever valid() is false, unconditionally.
        final LicenseSource src = () -> entitlements(LicenseState.EXPIRED, Set.of("Cat.ALPHA"));

        assertThrows(LicenseException.class, () -> LicenseGuard.require(src, ALPHA));
    }

    @Test
    public void test_unlicensedDeployment_refusesEverything() {
        final LicenseSource src = () -> ClaimsEntitlements.invalid(LicenseState.DATABASE_NOT_LICENSED);

        assertThrows(LicenseException.class, () -> LicenseGuard.require(src, ALPHA));
    }

    @Test
    public void test_noLicense_refuses() {
        final LicenseSource src = () -> ClaimsEntitlements.invalid(LicenseState.NO_LICENSE);

        assertThrows(LicenseException.class, () -> LicenseGuard.require(src, ALPHA));
    }

    @Test
    public void test_reReadsEveryTime_andCachesNothing() {
        // the whole reason a gate may not capture a boolean: validity flips as exp passes.
        final AtomicReference<Entitlements> current =
                new AtomicReference<>(entitlements(LicenseState.VALID, Set.of("Cat.ALPHA")));
        final AtomicInteger calls = new AtomicInteger();
        final CountingSource src = new CountingSource(current, calls);

        assertDoesNotThrow(() -> LicenseGuard.require(src, ALPHA));
        assertEquals(1, calls.get());

        current.set(entitlements(LicenseState.EXPIRED, Set.of("Cat.ALPHA")));

        assertThrows(LicenseException.class, () -> LicenseGuard.require(src, ALPHA));
        assertEquals(2, calls.get(), "the gate must consult the source again, not reuse an answer");
    }

    @Test
    public void test_exceptionIsUnchecked() {
        // callers of a capability entry point cannot do anything useful with a checked exception.
        assertTrue(RuntimeException.class.isAssignableFrom(LicenseException.class));
    }

    @Test
    public void test_expiresAtIsNotConsultedByTheGate() {
        // the gate asks has(f) and nothing else; the horizon is the admin page's business.
        final LicenseSource src = () -> entitlements(LicenseState.VALID, Set.of("Cat.ALPHA"));

        assertDoesNotThrow(() -> LicenseGuard.require(src, ALPHA));
        assertEquals(Optional.of(Instant.parse("2027-01-01T00:00:00Z")), src.current().expiresAt());
    }
}
