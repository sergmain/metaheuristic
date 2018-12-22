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
