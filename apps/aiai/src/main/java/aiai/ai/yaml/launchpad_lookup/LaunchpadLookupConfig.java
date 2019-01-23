/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.yaml.launchpad_lookup;

import aiai.apps.commons.utils.SecUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

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
        public boolean isSecureRestUrl;
        public String taskProcessingTime;
        public String restPassword;
        public String restUsername;
        public String restToken;
        public boolean isAcceptOnlySignedSnippets;

        public PublicKey createPublicKey() {
            return SecUtils.getPublicKey(publicKey);
        }
    }
}
