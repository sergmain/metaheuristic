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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Data classes for the dispatcher Key Vault.
 * Vault stores entries in form accountId:code:key (multi-tenant by accountId).
 *
 * @author Sergio Lissner
 */
public class VaultData {

    /** Status of the vault — whether it is currently unlocked in dispatcher memory. */
    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class VaultStatus extends BaseDataClass {
        public boolean opened;

        public VaultStatus(boolean opened) {
            this.opened = opened;
        }

        public VaultStatus(boolean opened, String error) {
            this.opened = opened;
            addErrorMessage(error);
        }

        @JsonCreator
        public VaultStatus(
                @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    /** Result of an unlock attempt; {@code created}=true if vault file did not exist and was created. */
    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class UnlockResult extends BaseDataClass {
        public boolean opened;
        public boolean created;

        public UnlockResult(boolean opened, boolean created) {
            this.opened = opened;
            this.created = created;
        }

        public UnlockResult(String error) {
            this.opened = false;
            this.created = false;
            addErrorMessage(error);
        }

        @JsonCreator
        public UnlockResult(
                @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    /** Request body for unlock. */
    public record UnlockRequest(String passphrase) {}
}
