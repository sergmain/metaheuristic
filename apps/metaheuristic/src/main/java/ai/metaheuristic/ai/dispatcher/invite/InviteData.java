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

import org.jspecify.annotations.Nullable;

/**
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public class InviteData {

    /** What the operator gets back after creating an invite. */
    public record CreatedInvite(boolean ok, @Nullable String errorMessage, @Nullable Long inviteId,
                                @Nullable String token, long expiredOn) {
        public static CreatedInvite error(String msg) {
            return new CreatedInvite(false, msg, null, null, 0L);
        }
    }

    /**
     * What the redeemer gets back.
     *
     * <p>The raw password is returned EXACTLY ONCE and is never recoverable
     * afterwards — only its BCrypt hash is stored. A redeemer that loses it
     * needs a new invite, which is the correct outcome: recovering it would
     * require storing it reversibly, and a reversible credential store is
     * strictly worse than reissuing.
     *
     * <p>On refusal every field but {@link #ok} is null and
     * {@link #errorMessage} is a fixed opaque string — see
     * {@code InviteTxService} for why the reason is not disclosed here.
     */
    public record RedeemedInvite(boolean ok, @Nullable String errorMessage,
                                 @Nullable String username, @Nullable String rawPassword,
                                 @Nullable Long accountId, @Nullable Long companyId) {
        public static RedeemedInvite error(String msg) {
            return new RedeemedInvite(false, msg, null, null, null, null);
        }
    }
}
