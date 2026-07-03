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

package ai.metaheuristic.apps.license_signer;

import ai.metaheuristic.commons.spi.license.JwsSigner;
import ai.metaheuristic.commons.spi.license.LicenseClaimsBuilder;
import ai.metaheuristic.commons.spi.license.LicenseClaimsV1;
import ai.metaheuristic.api.data.license.LicenseConfigYaml;
import ai.metaheuristic.commons.yaml.license.LicenseConfigYamlUtils;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.Date;

/**
 * Generic, proprietary-free license-file creator.
 *
 * Reads a YAML recipe ({@link LicenseConfigYaml}), resolves it into {@link LicenseClaimsV1}, and
 * writes a compact JWS license file (ES256). Feature keys are opaque strings taken verbatim from the
 * config - the tool never expands an 'edition' into a feature closure, so no proprietary closure concept
 * enters MH. The 'signing' section of the config governs the key/output and never enters the token.
 *
 * @author Serge
 */
@SpringBootApplication
public class LicenseSigner implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(LicenseSigner.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length==0) {
            printUsage();
            return;
        }
        final String cmd = args[0];
        if ("gen-key".equals(cmd)) {
            genKey();
            return;
        }
        if ("sign".equals(cmd)) {
            if (args.length<2) {
                System.out.println("249.010 usage: sign <license-config.yaml>");
                return;
            }
            sign(Path.of(args[1]));
            return;
        }
        printUsage();
    }

    private static void printUsage() {
        System.out.println("""
                license-signer - creates a signed license file (compact JWS, ES256).

                Commands:
                  gen-key                       generate an EC P-256 signing keypair (base64 PKCS#8 / X.509)
                  sign <license-config.yaml>    read the YAML recipe and write the .jws license file

                The YAML config carries opaque feature-key strings; no feature closure is computed here.
                """);
    }

    private static void genKey() throws Exception {
        final KeyPair kp = EcP256Keys.generate();
        System.out.println("EC P-256 private key (base64 PKCS#8):\n" + EcP256Keys.encodeBase64(kp.getPrivate()) + "\n");
        System.out.println("EC P-256 public key (base64 X.509):\n" + EcP256Keys.encodeBase64(kp.getPublic()));
        System.out.println("""

                The label lines above are not part of the keys and must not be stored in a key file.
                Store the private key (base64 only) in the file referenced by signing.privateKeyFile.
                """);
    }

    private static void sign(Path configFile) throws Exception {
        if (Files.notExists(configFile)) {
            System.out.println("249.020 config file doesn't exist: " + configFile);
            return;
        }
        final String yaml = Files.readString(configFile, StandardCharsets.UTF_8);
        final LicenseConfigYaml config = LicenseConfigYamlUtils.BASE_YAML_UTILS.to(yaml);

        final LicenseConfigYaml.Signing signing = config.signing;
        if (!"ES256".equals(signing.algorithm)) {
            System.out.println("249.030 only ES256 is supported by this backend, got: " + signing.algorithm);
            return;
        }
        if (signing.kid==null || signing.kid.isBlank()) {
            System.out.println("249.040 signing.kid must be set");
            return;
        }
        if (signing.outputFile==null || signing.outputFile.isBlank()) {
            System.out.println("249.050 signing.outputFile must be set");
            return;
        }
        if (signing.privateKeyFile==null || signing.privateKeyFile.isBlank()) {
            System.out.println("249.060 signing.privateKeyFile must be set");
            return;
        }
        final Path keyFile = Path.of(signing.privateKeyFile);
        if (Files.notExists(keyFile)) {
            System.out.println("249.070 private key file doesn't exist: " + keyFile);
            return;
        }

        // LicenseClaimsBuilder enforces the timeless-requires-required_profiles safety rule.
        final LicenseClaimsV1 claims;
        try {
            claims = LicenseClaimsBuilder.build(config.license, Instant.now());
        }
        catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            return;
        }

        final ECPrivateKey privateKey = EcP256Keys.readPrivateKey(Files.readString(keyFile, StandardCharsets.UTF_8));
        final JwsSigner jwsSigner = new LocalEcP256JwsSigner(privateKey);

        final String headerJson = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signing.kid)
                .type(new JOSEObjectType("license+jws"))
                .build()
                .toString();
        final String payloadJson = toPayloadJson(claims);

        final String jws = jwsSigner.sign(headerJson, payloadJson);

        final Path out = Path.of(signing.outputFile);
        Files.writeString(out, jws, StandardCharsets.UTF_8);

        System.out.println("License written: " + out.toAbsolutePath());
        System.out.println("  licensee : " + claims.licensee);
        System.out.println("  edition  : " + claims.edition);
        System.out.println("  features : " + claims.features);
        System.out.println("  exp      : " + (claims.exp==null ? "never (deployment-limited)" : claims.exp));
    }

    private static String toPayloadJson(LicenseClaimsV1 claims) {
        final JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .claim("licensee", claims.licensee)
                .claim("edition", claims.edition)
                .claim("features", claims.features)
                .claim("ver", claims.ver);
        if (claims.iat!=null) {
            b.issueTime(Date.from(claims.iat));
        }
        if (claims.nbf!=null) {
            b.notBeforeTime(Date.from(claims.nbf));
        }
        if (claims.exp!=null) {
            b.expirationTime(Date.from(claims.exp));
        }
        if (!claims.requiredProfiles.isEmpty()) {
            b.claim("required_profiles", claims.requiredProfiles);
        }
        if (!claims.forbiddenProfiles.isEmpty()) {
            b.claim("forbidden_profiles", claims.forbiddenProfiles);
        }
        if (claims.installationId!=null && !claims.installationId.isBlank()) {
            b.claim("installation_id", claims.installationId);
        }
        return b.build().toString();
    }
}
