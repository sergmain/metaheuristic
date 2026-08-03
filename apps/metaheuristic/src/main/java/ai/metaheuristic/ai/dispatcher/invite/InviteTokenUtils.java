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

package ai.metaheuristic.ai.dispatcher.invite;

import ai.metaheuristic.ai.dispatcher.beans.Invite;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secret generation and redemption eligibility for {@link Invite} — static and
 * free of Spring, so both are exercisable without a context or a database.
 *
 * <p><b>{@link SecureRandom}, not {@link java.util.Random}.</b> A token that
 * authorizes minting an account is a credential; {@code Random} is a linear
 * congruential generator whose entire future output is recoverable from a
 * couple of observed values, which for an invite scheme means one recipient can
 * derive everyone else's tokens.
 *
 * <p><b>Base64url, no padding.</b> Two hard downstream constraints shape this,
 * and both are silent if violated:
 * <ul>
 *   <li>{@code mh_account.USERNAME} is {@code varchar(50)}. 128 bits of
 *       Base64url is 22 characters, leaving ample headroom.</li>
 *   <li>{@code AccountTxService.addAccount} rejects {@code '='} in a username,
 *       and HTTP Basic splits credentials on {@code ':'}. The Base64url
 *       alphabet ({@code A-Za-z0-9-_}) contains neither, and dropping padding
 *       removes the only source of {@code '='}.</li>
 * </ul>
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public class InviteTokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * 128 bits -> 22 chars, well inside {@code USERNAME varchar(50)}.
     *
     * <p>Not raised now that the column is wider: 128 bits is already far past
     * any collision concern for an identifier, so extra length would buy
     * nothing and only make the username harder to handle by hand.
     */
    public static final int USERNAME_BYTES = 16;

    /** 256 bits -> 43 chars. Fits {@code TOKEN varchar(50)}. */
    public static final int TOKEN_BYTES = 32;

    /** 256 bits -> 43 chars. Only the BCrypt hash is stored, so length is unconstrained. */
    public static final int PASSWORD_BYTES = 32;

    public static final long DEFAULT_TTL_MILLIS = 7L * 24 * 3600 * 1000;

    private static String randomString(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String newToken() {
        return randomString(TOKEN_BYTES);
    }

    /** Globally unique by construction, so it never collides with {@code mh_account_username_unq_idx}. */
    public static String newUsername() {
        return randomString(USERNAME_BYTES);
    }

    public static String newPassword() {
        return randomString(PASSWORD_BYTES);
    }

    /**
     * Whether this invite may be redeemed right now.
     *
     * <p>Returns a REASON rather than a boolean because the three ways an
     * invite can be unusable — already redeemed, expired, withdrawn — need to
     * be distinguishable in an operator's audit trail, even though the caller
     * deliberately collapses them into one opaque refusal on the wire.
     *
     * @param nowMillis injected rather than read from the clock, so expiry
     *                  boundaries are testable without sleeping
     * @return {@code null} when redeemable, otherwise a short reason code
     */
    @Nullable
    public static String redemptionRefusalReason(@Nullable Invite invite, long nowMillis) {
        if (invite==null) {
            return "notFound";
        }
        if (invite.deleted) {
            return "withdrawn";
        }
        if (invite.invitedAccountId!=null) {
            return "alreadyRedeemed";
        }
        if (nowMillis >= invite.expiredOn) {
            return "expired";
        }
        return null;
    }

    public static boolean isRedeemable(@Nullable Invite invite, long nowMillis) {
        return redemptionRefusalReason(invite, nowMillis)==null;
    }
}
