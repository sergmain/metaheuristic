/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.mh.dispatcher._lookup;

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
public class DispatcherLookupConfig {
    public List<DispatcherLookup> mh.dispatcher.s = new ArrayList<>();

    public enum DispatcherLookupType {
        direct, registry
    }

    public enum AuthType {
        basic, oauth
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Asset {
        public String url;
        public String username;
        public String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherLookup {
        // fields, which are specific to concrete installation
        // actually, it's a schedule
        public String taskProcessingTime;

        // common fields
        public boolean disabled;
        public String url;
        public boolean signatureRequired;
        public String publicKey;
        public DispatcherLookupType lookupType;
        public AuthType authType;

        public String restPassword;
        public String restUsername;
        public Asset asset;

        public boolean acceptOnlySignedFunctions = false;

        private final Map<Integer, PublicKey> publicKeyMap = new HashMap<>();

        public PublicKey createPublicKey() {
            return publicKeyMap.computeIfAbsent(1, o-> SecUtils.getPublicKey(this.publicKey));
        }
    }
}
