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

package ai.metaheuristic.ai.yaml.company;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * @author Serge
 * Date: 10/3/2019
 * Time: 4:51 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyParamsYaml implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AccessControl {
        public String groups;
    }

    /**
     * Per-company Key Vault, embedded in Company.params.
     *
     * <p>{@code salt} (Base64) and {@code iterations} are the PBKDF2 parameters
     * used to derive a 256-bit AES key from the company's master passphrase.
     * {@code encryptedEntries} (Base64) is the AES/GCM-encrypted JSON map of
     * {@code {code: secret}}. IV is the first 12 bytes of the decoded blob;
     * the GCM auth tag is the trailing 16 bytes.
     *
     * <p>The salt + iterations are bound as AAD to GCM, so tampering with
     * those fields fails authentication on decrypt.
     *
     * <p>This is a {@code @Nullable} field added without a version bump per the
     * multi-versioning {@code @Nullable}-exception rule: SnakeYAML is configured
     * with {@code setSkipMissingProperties(true)}, so older V2 YAML documents
     * that lack this field deserialize fine — {@code vault} stays {@code null}.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VaultEntries {
        /** Base64-encoded salt (16 bytes). */
        public String salt;
        /** PBKDF2 iteration count. */
        public int iterations;
        /** Base64-encoded {@code [12-byte IV || ciphertext || 16-byte auth tag]}. */
        public String encryptedEntries;
    }

    public long createdOn;
    public long updatedOn;

    public @Nullable AccessControl ac;

    /** Per-company encrypted Key Vault. Null until the user creates the vault. */
    public @Nullable VaultEntries vault;

}
