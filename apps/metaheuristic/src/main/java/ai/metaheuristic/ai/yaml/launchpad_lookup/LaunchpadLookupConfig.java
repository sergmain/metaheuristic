/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.launchpad_lookup;

import ai.metaheuristic.commons.utils.SecUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class LaunchpadLookupConfig {
    public List<LaunchpadLookup> launchpads = new ArrayList<>();

    public enum LaunchpadLookupType {
        direct, registry
    }

    public enum AuthType {
        basic, oauth
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaunchpadLookup {
        public boolean disabled;
        public String url;
        public boolean signatureRequired;
        public String publicKey;
        public LaunchpadLookupType lookupType;
        public AuthType authType;
        public boolean securityEnabled;
        public String taskProcessingTime;
        public String restPassword;
        public String restUsername;

        /**
         * won't delete this field for backward compatibility
         */
        @Deprecated
        public String restToken;
        // for backward compatibility
        public String getRestToken() {
            return "";
        }


        public boolean acceptOnlySignedSnippets = false;

        private final Map<Integer, PublicKey> publicKeyMap = new HashMap<>();

        public PublicKey createPublicKey() {
            return publicKeyMap.computeIfAbsent(1, o-> SecUtils.getPublicKey(this.publicKey));
        }
    }
}
