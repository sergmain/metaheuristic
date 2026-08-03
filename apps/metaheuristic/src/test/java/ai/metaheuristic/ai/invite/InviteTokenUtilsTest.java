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

package ai.metaheuristic.ai.invite;

import ai.metaheuristic.ai.dispatcher.beans.Invite;
import ai.metaheuristic.ai.dispatcher.invite.InviteTokenUtils;
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
public class InviteTokenUtilsTest {

    private static final long NOW = 1_000_000L;

    private static Invite invite(long expiredOn) {
        Invite i = new Invite();
        i.companyId = 7L;
        i.accountId = 42L;
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
            String t = InviteTokenUtils.newToken();

            assertEquals(43, t.length(), t);
            assertTrue(t.length() <= 50, t);
            assertTrue(t.matches("[A-Za-z0-9_-]+"), t);
        }
    }

    @Test
    public void test_newPassword_hasNoBasicAuthSeparator() {
        for (int i = 0; i < 200; i++) {
            String p = InviteTokenUtils.newPassword();

            assertEquals(43, p.length(), p);
            assertFalse(p.contains(":"), p);
        }
    }

    /**
     * Not a randomness test — a cheap guard that generation is actually random
     * rather than seeded or constant, which is the failure mode that would make
     * every invite in an installation identical.
     */
    @Test
    public void test_generatedSecretsDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(InviteTokenUtils.newToken());
            passwords.add(InviteTokenUtils.newPassword());
        }
        assertEquals(1000, tokens.size());
        assertEquals(1000, passwords.size());
    }

    // ---------- redemption eligibility ----------

    @Test
    public void test_liveInvite_isRedeemable() {
        Invite i = invite(NOW + 1);

        assertNull(InviteTokenUtils.redemptionRefusalReason(i, NOW));
        assertTrue(InviteTokenUtils.isRedeemable(i, NOW));
    }

    @Test
    public void test_missingInvite_isRefused() {
        assertEquals("notFound", InviteTokenUtils.redemptionRefusalReason(null, NOW));
        assertFalse(InviteTokenUtils.isRedeemable(null, NOW));
    }

    @Test
    public void test_withdrawnInvite_isRefused() {
        Invite i = invite(NOW + 1);
        i.deleted = true;

        assertEquals("withdrawn", InviteTokenUtils.redemptionRefusalReason(i, NOW));
    }

    /** The single-use guarantee: a spent invite is refused forever after. */
    @Test
    public void test_redeemedInvite_isRefused() {
        Invite i = invite(NOW + 1);
        i.redeemedOn = NOW;

        assertEquals("alreadyRedeemed", InviteTokenUtils.redemptionRefusalReason(i, NOW));
        assertEquals("alreadyRedeemed",
                InviteTokenUtils.redemptionRefusalReason(i, NOW + 10_000_000L));
    }

    /** Expiry is inclusive at the deadline — at expiredOn the invite is already dead. */
    @Test
    public void test_expiryBoundary() {
        Invite i = invite(NOW);

        assertEquals("expired", InviteTokenUtils.redemptionRefusalReason(i, NOW));
        assertEquals("expired", InviteTokenUtils.redemptionRefusalReason(i, NOW + 1));
        assertNull(InviteTokenUtils.redemptionRefusalReason(i, NOW - 1));
    }

    /**
     * Refusal precedence is deterministic, so an operator reading the log for a
     * doubly-invalid token always sees the same reason.
     */
    @Test
    public void test_withdrawnBeatsRedeemedBeatsExpired() {
        Invite withdrawnAndRedeemed = invite(NOW - 1);
        withdrawnAndRedeemed.deleted = true;
        withdrawnAndRedeemed.redeemedOn = NOW;
        assertEquals("withdrawn", InviteTokenUtils.redemptionRefusalReason(withdrawnAndRedeemed, NOW));

        Invite redeemedAndExpired = invite(NOW - 1);
        redeemedAndExpired.redeemedOn = NOW;
        assertEquals("alreadyRedeemed", InviteTokenUtils.redemptionRefusalReason(redeemedAndExpired, NOW));
    }
}
