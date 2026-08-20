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

import java.util.EnumSet;
import java.util.Set;
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
    public void test_headerContractFailuresAreNotInstallable() {
        // a token this dispatcher cannot even select a key for can never become valid here, so
        // storing it would only put a permanent dead row in the set.
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.UNKNOWN_KID));
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.MISSING_KID));
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.UNSUPPORTED_ALGORITHM));
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.WRONG_TOKEN_TYPE));
        // with no key configured on this installation nothing can ever verify, so the row would
        // be dead from the moment it was written.
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.NO_VERIFICATION_KEY));
        // the three causes that used to hide inside UNSUPPORTED_ALGORITHM / SIGNATURE_INVALID:
        // naming them changed what the admin is told, not what may be stored.
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.UNSIGNED_TOKEN));
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.ENCRYPTED_TOKEN));
        assertFalse(LicenseArtifactUtils.isInstallable(LicenseState.UNPARSEABLE));
    }

    @Test
    public void test_everyStateIsDecided() {
        // total by construction: a state added later must not silently become un-installable.
        final Set<LicenseState> refused = EnumSet.of(
                LicenseState.SIGNATURE_INVALID, LicenseState.MALFORMED, LicenseState.UNKNOWN_KID,
                LicenseState.MISSING_KID, LicenseState.UNSUPPORTED_ALGORITHM, LicenseState.WRONG_TOKEN_TYPE,
                LicenseState.NO_VERIFICATION_KEY, LicenseState.UNSIGNED_TOKEN,
                LicenseState.ENCRYPTED_TOKEN, LicenseState.UNPARSEABLE);
        for (LicenseState state : LicenseState.values()) {
            assertEquals(!refused.contains(state), LicenseArtifactUtils.isInstallable(state), state.name());
        }
    }

    /**
     * NO_VERIFICATION_KEY means this dispatcher holds no key material at all, so the token was
     * never examined - nothing whatsoever is known about whether it is a valid license.
     */
    @Test
    public void test_noVerificationKeyRefusalNamesTheFault() {
        final String message = LicenseArtifactUtils.refusalMessage(LicenseState.NO_VERIFICATION_KEY);

        assertEquals("01.267.010 no license verification key is configured on this dispatcher "
                + "(mh.key-store.license.public-key), so no license can be verified here - the "
                + "license itself was never examined, state: NO_VERIFICATION_KEY", message);
    }

    /** A refusal that IS about the token keeps the generic wording. */
    @Test
    public void test_tokenFaultRefusalsKeepTheGenericMessage() {
        assertEquals("01.267.020 this is not a valid license, state: SIGNATURE_INVALID",
                LicenseArtifactUtils.refusalMessage(LicenseState.SIGNATURE_INVALID));
        assertEquals("01.267.020 this is not a valid license, state: UNKNOWN_KID",
                LicenseArtifactUtils.refusalMessage(LicenseState.UNKNOWN_KID));
    }

    @Test
    public void test_everyStateGetsAMessageCarryingACode() {
        // total by construction: a state added later must not produce a message with no code to
        // grep for, which is the one thing an error message exists to provide.
        for (LicenseState state : LicenseState.values()) {
            final String message = LicenseArtifactUtils.refusalMessage(state);
            assertTrue(message.startsWith("01.267."), state.name() + " -> " + message);
            assertTrue(message.contains(state.name()), state.name() + " -> " + message);
        }
    }
}
