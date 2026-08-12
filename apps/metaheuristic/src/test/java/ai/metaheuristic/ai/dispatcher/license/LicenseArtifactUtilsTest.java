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

import ai.metaheuristic.commons.spi.license.LicenseState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static ai.metaheuristic.ai.dispatcher.license.LicenseArtifactUtils.InstallAction;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The two decisions behind installing a license. Spring-less on purpose — neither of them needs a
 * database to be decided, so neither needs one to be tested.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseArtifactUtilsTest {

    @Test
    public void test_noRow_isCreate() {
        assertEquals(InstallAction.CREATE, LicenseArtifactUtils.decideInstall(null));
    }

    @Test
    public void test_liveRow_isNoop() {
        // re-installing a license you already hold is not a mistake worth reporting.
        assertEquals(InstallAction.NOOP, LicenseArtifactUtils.decideInstall(Boolean.FALSE));
    }

    @Test
    public void test_removedRow_isRevive() {
        // TOKEN_HASH is unique, so a second insert would collide; the removed row is un-removed.
        assertEquals(InstallAction.REVIVE, LicenseArtifactUtils.decideInstall(Boolean.TRUE));
    }

    @Test
    public void test_unverifiableStatesAreRefused() {
        // the only two that mean "this is not a license of ours".
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.SIGNATURE_INVALID));
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.MALFORMED));
    }

    @Test
    public void test_validIsInstallable() {
        assertTrue(LicenseArtifactUtils.isInstallable(LicenseState.VALID));
    }

    @Test
    public void test_expiredIsInstallable() {
        // a license valid today is EXPIRED tomorrow and stays in the set, so refusing to install
        // one would be inconsistent with the state the system reaches on its own.
        assertTrue(LicenseArtifactUtils.isInstallable(LicenseState.EXPIRED));
    }

    @Test
    public void test_notYetValidIsInstallable() {
        // installing ahead of the window is a normal thing to do.
        assertTrue(LicenseArtifactUtils.isInstallable(LicenseState.NOT_YET_VALID));
    }

    @Test
    public void test_installIdMismatchIsInstallable() {
        // listing it tells the admin WHICH of the five things went wrong; a blanket refusal
        // would only be able to say "invalid".
        assertTrue(LicenseArtifactUtils.isInstallable(LicenseState.INSTALL_ID_MISMATCH));
    }

    @Test
    public void test_everyStateIsDecided() {
        // total by construction: a state added later must not silently become un-installable.
        for (LicenseState state : LicenseState.values()) {
            final boolean expected = state != LicenseState.SIGNATURE_INVALID && state != LicenseState.MALFORMED;
            assertEquals(expected, LicenseArtifactUtils.isInstallable(state), state.name());
        }
    }
}
