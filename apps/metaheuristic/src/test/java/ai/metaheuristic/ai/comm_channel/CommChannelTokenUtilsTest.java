/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.comm_channel;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.dispatcher.beans.CommChannel;
import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelTokenUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Execution(CONCURRENT)
public class CommChannelTokenUtilsTest {

    private static final long NOW = 1_000_000L;

    private static CommChannel channel(long expiredOn) {
        CommChannel i = new CommChannel();
        i.companyId = 7L;
        i.serviceTag = "svc";
        i.grantedRole = "ROLE_SOMETHING";
        i.createdByAccountId = 1L;
        i.token = "t";
        i.expiredOn = expiredOn;
        i.deleted = false;
        return i;
    }

    // ---------- generated secrets ----------

    /** TOKEN is varchar(50). */
    @Test
    public void test_newToken_fitsColumn() {
        for (int i = 0; i < 200; i++) {
            String t = CommChannelTokenUtils.newToken();

            assertEquals(43, t.length(), t);
            assertTrue(t.length() <= 50, t);
            assertTrue(t.matches("[A-Za-z0-9_-]+"), t);
        }
    }

    /** USERNAME is varchar(50); a generated username that overflows it fails at insert. */
    @Test
    public void test_newUsername_fitsColumnAndCharsetConstraints() {
        for (int i = 0; i < 200; i++) {
            String u = CommChannelTokenUtils.newUsername();

            assertEquals(22, u.length(), u);
            assertTrue(u.length() <= 50, u);
            // addAccount rejects '='; HTTP Basic splits credentials on ':'
            assertFalse(u.contains("="), u);
            assertFalse(u.contains(":"), u);
            assertTrue(u.matches("[A-Za-z0-9_-]+"), u);
        }
    }

    @Test
    public void test_newPassword_hasNoBasicAuthSeparator() {
        for (int i = 0; i < 200; i++) {
            String p = CommChannelTokenUtils.newPassword();

            assertEquals(43, p.length(), p);
            assertFalse(p.contains(":"), p);
        }
    }

    /**
     * Not a randomness test — a cheap guard that generation is actually random
     * rather than seeded or constant, which is the failure mode that would make
     * every channel in an installation identical.
     */
    @Test
    public void test_generatedSecretsDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(CommChannelTokenUtils.newToken());
            passwords.add(CommChannelTokenUtils.newPassword());
        }
        assertEquals(1000, tokens.size());
        assertEquals(1000, passwords.size());
    }

    // ---------- redemption eligibility ----------

    @Test
    public void test_livechannel_isActivatable() {
        CommChannel i = channel(NOW + 1);

        assertEquals(EnumsApi.ActivationCheck.ok, CommChannelTokenUtils.checkActivatable(i, NOW));
        assertTrue(CommChannelTokenUtils.isActivatable(i, NOW));
    }

    @Test
    public void test_missingchannel_isRefused() {
        assertEquals(EnumsApi.ActivationCheck.notFound, CommChannelTokenUtils.checkActivatable(null, NOW));
        assertFalse(CommChannelTokenUtils.isActivatable(null, NOW));
        assertFalse(EnumsApi.ActivationCheck.notFound.isOk());
    }

    @Test
    public void test_withdrawnchannel_isRefused() {
        CommChannel i = channel(NOW + 1);
        i.deleted = true;

        assertEquals(EnumsApi.ActivationCheck.withdrawn, CommChannelTokenUtils.checkActivatable(i, NOW));
    }

    /** The single-use guarantee: a spent channel is refused forever after. */
    @Test
    public void test_redeemedchannel_isRefused() {
        CommChannel i = channel(NOW + 1);
        i.activatedOn = NOW;

        assertEquals(EnumsApi.ActivationCheck.alreadyActivated, CommChannelTokenUtils.checkActivatable(i, NOW));
        assertEquals(EnumsApi.ActivationCheck.alreadyActivated,
                CommChannelTokenUtils.checkActivatable(i, NOW + 10_000_000L));
    }

    /** Expiry is inclusive at the deadline — at expiredOn the channel is already dead. */
    @Test
    public void test_expiryBoundary() {
        CommChannel i = channel(NOW);

        assertEquals(EnumsApi.ActivationCheck.expired, CommChannelTokenUtils.checkActivatable(i, NOW));
        assertEquals(EnumsApi.ActivationCheck.expired, CommChannelTokenUtils.checkActivatable(i, NOW + 1));
        assertEquals(EnumsApi.ActivationCheck.ok, CommChannelTokenUtils.checkActivatable(i, NOW - 1));
    }

    /**
     * Refusal precedence is deterministic, so an operator reading the log for a
     * doubly-invalid token always sees the same reason.
     */
    @Test
    public void test_withdrawnBeatsRedeemedBeatsExpired() {
        CommChannel withdrawnAndActivated = channel(NOW - 1);
        withdrawnAndActivated.deleted = true;
        withdrawnAndActivated.activatedOn = NOW;
        assertEquals(EnumsApi.ActivationCheck.withdrawn, CommChannelTokenUtils.checkActivatable(withdrawnAndActivated, NOW));

        CommChannel activatedAndExpired = channel(NOW - 1);
        activatedAndExpired.activatedOn = NOW;
        assertEquals(EnumsApi.ActivationCheck.alreadyActivated, CommChannelTokenUtils.checkActivatable(activatedAndExpired, NOW));
    }
}
