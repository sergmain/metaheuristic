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

import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public class InviteData {

    /** What the operator gets back after creating an invite. */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class CreatedInvite extends BaseDataClass {
        @Nullable
        public Long inviteId;
        @Nullable
        public String token;
        public long expiredOn;

        @JsonCreator
        public CreatedInvite(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public CreatedInvite(Long inviteId, String token, long expiredOn) {
            this.inviteId = inviteId;
            this.token = token;
            this.expiredOn = expiredOn;
        }

        public static CreatedInvite error(String msg) {
            return new CreatedInvite(List.of(msg), null);
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
     * <p>On refusal every payload field stays null and {@code errorMessages}
     * carries one fixed opaque string — see {@link InviteTxService} for why the
     * reason is not disclosed.
     */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class RedeemedInvite extends BaseDataClass {
        @Nullable
        public String username;
        @Nullable
        public String rawPassword;
        @Nullable
        public Long accountId;
        @Nullable
        public Long companyId;

        @JsonCreator
        public RedeemedInvite(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public RedeemedInvite(String username, String rawPassword, Long accountId, Long companyId) {
            this.username = username;
            this.rawPassword = rawPassword;
            this.accountId = accountId;
            this.companyId = companyId;
        }

        public static RedeemedInvite error(String msg) {
            return new RedeemedInvite(List.of(msg), null);
        }
    }
}
