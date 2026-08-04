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

package ai.metaheuristic.ai.dispatcher.comm_channel;

import ai.metaheuristic.ai.dispatcher.beans.CommChannel;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secret generation and activation eligibility for {@link CommChannel} — static and
 * free of Spring, so both are exercisable without a context or a database.
 *
 * <p><b>{@link SecureRandom}, not {@link java.util.Random}.</b> A token that
 * authorizes minting an account is a credential; {@code Random} is a linear
 * congruential generator whose entire future output is recoverable from a
 * couple of observed values, which for a channel scheme means one recipient can
 * derive everyone else's tokens.
 *
 * <p><b>Base64url, no padding.</b> Three downstream constraints shape this, and
 * all three are silent if violated:
 * <ul>
 *   <li>Username and password travel in HTTP Basic credentials, which are split
 *       on {@code ':'} — the Base64url alphabet ({@code A-Za-z0-9-_}) has
 *       none.</li>
 *   <li>{@code AccountTxService.addAccount} rejects {@code '='} in a username;
 *       dropping padding removes the only source of it.</li>
 *   <li>{@code TOKEN} and {@code USERNAME} are both {@code varchar(50)} —
 *       256 bits of Base64url is 43 characters, 128 bits is 22.</li>
 * </ul>
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public class CommChannelTokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 256 bits -> 43 chars. Fits {@code TOKEN varchar(50)}. */
    public static final int TOKEN_BYTES = 32;

    /** 256 bits -> 43 chars. Only the BCrypt hash is stored, so length is unconstrained. */
    public static final int PASSWORD_BYTES = 32;

    /**
     * 128 bits -> 22 chars, well inside {@code USERNAME varchar(50)}.
     *
     * <p>The username is OPAQUE: nothing binds to it in advance, nothing matches
     * on it, and it is never displayed as identity. Generating it randomly makes
     * it globally unique by construction, so it can never collide with
     * {@code mh_account_username_unq_idx}, and an unauthenticated caller never
     * gets to choose an identifier other people will see.
     */
    public static final int USERNAME_BYTES = 16;

    public static final long DEFAULT_TTL_MILLIS = 7L * 24 * 3600 * 1000;

    private static String randomString(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String newToken() {
        return randomString(TOKEN_BYTES);
    }

    public static String newPassword() {
        return randomString(PASSWORD_BYTES);
    }

    /** Globally unique by construction; see {@link #USERNAME_BYTES}. */
    public static String newUsername() {
        return randomString(USERNAME_BYTES);
    }

    /**
     * Whether this channel may be activated right now.
     *
     * <p>Returns a REASON rather than a boolean because the three ways an
     * channel can be unusable — already activated, expired, withdrawn — need to
     * be distinguishable in an operator's audit trail, even though the caller
     * deliberately collapses them into one opaque refusal on the wire.
     *
     * @param nowMillis injected rather than read from the clock, so expiry
     *                  boundaries are testable without sleeping
     * @return {@code null} when redeemable, otherwise a short reason code
     */
    @Nullable
    public static String activationRefusalReason(@Nullable CommChannel channel, long nowMillis) {
        if (channel==null) {
            return "notFound";
        }
        if (channel.deleted) {
            return "withdrawn";
        }
        if (channel.activatedOn!=null) {
            return "alreadyActivated";
        }
        if (nowMillis >= channel.expiredOn) {
            return "expired";
        }
        return null;
    }

    public static boolean isActivatable(@Nullable CommChannel channel, long nowMillis) {
        return activationRefusalReason(channel, nowMillis)==null;
    }
}
