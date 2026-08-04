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
public class CommChannelData {

    /** What the operator gets back after issuing a channel token. */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class IssuedChannel extends BaseDataClass {
        @Nullable
        public Long channelId;
        @Nullable
        public String token;
        @Nullable
        public String serviceTag;
        public long expiredOn;

        @JsonCreator
        public IssuedChannel(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public IssuedChannel(Long channelId, String token, String serviceTag, long expiredOn) {
            this.channelId = channelId;
            this.token = token;
            this.serviceTag = serviceTag;
            this.expiredOn = expiredOn;
        }

        public static IssuedChannel error(String msg) {
            return new IssuedChannel(List.of(msg), null);
        }
    }

    /**
     * What the outside party gets back on activation.
     *
     * <p>The raw password is returned EXACTLY ONCE and is never recoverable
     * afterwards — only its BCrypt hash is stored. Losing it means needing a new
     * channel, which is the correct outcome: recovering it would require storing
     * it reversibly, and a reversible credential store is strictly worse than
     * reissuing.
     *
     * <p>On refusal every payload field stays null and {@code errorMessages}
     * carries one fixed opaque string — see {@link CommChannelTxService} for why
     * the reason is not disclosed.
     */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ActivatedChannel extends BaseDataClass {
        @Nullable
        public String username;
        @Nullable
        public String rawPassword;
        @Nullable
        public Long accountId;
        @Nullable
        public Long companyId;

        @JsonCreator
        public ActivatedChannel(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public ActivatedChannel(String username, String rawPassword, Long accountId, Long companyId) {
            this.username = username;
            this.rawPassword = rawPassword;
            this.accountId = accountId;
            this.companyId = companyId;
        }

        public static ActivatedChannel error(String msg) {
            return new ActivatedChannel(List.of(msg), null);
        }
    }
}
