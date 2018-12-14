package aiai.ai.yaml.signature;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class SignaturesConfig {
    public List<SignatureConfig> configs = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignatureConfig {
        public String url;
        public boolean signatureRequired;
        public String publicKey;
    }
}
