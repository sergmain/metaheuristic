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
import ai.metaheuristic.api.data.license.LicenseClaims;
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
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Date;

/**
 * Generic, proprietary-free license-file creator.
 * <br/>
 * Reads a YAML recipe ({@link LicenseConfigYaml}), resolves it into {@link LicenseClaims}, and
 * writes a compact JWS license file (ES256). Capability keys are opaque strings taken verbatim from
 * the config - the tool never expands an 'edition' into a capability closure, so no proprietary closure
 * concept enters MH. The deployment axes ('databases', 'storages') are plain MH values and are copied
 * just as verbatim. The 'signing' section of the config governs the key/output and never enters the token.
 *
 * @author Serge
 */
@SpringBootApplication
public class LicenseSigner implements CommandLineRunner {

    static void main(String[] args) {
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
            if (args.length<3) {
                System.out.println("249.010 usage: sign <license-config.yaml> (--key <private-key-file> | --mint-key)");
                return;
            }
            if ("--mint-key".equals(args[2])) {
                sign(Path.of(args[1]), null);
                return;
            }
            if ("--key".equals(args[2]) && args.length>3) {
                sign(Path.of(args[1]), Path.of(args[3]));
                return;
            }
            System.out.println("249.010 usage: sign <license-config.yaml> (--key <private-key-file> | --mint-key)");
            return;
        }
        printUsage();
    }

    /** {@code ./license.jws} -> {@code ./license}, so the three artifacts share one base name. */
    private static Path stripSuffix(Path outputFile) {
        final String name = outputFile.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot<1 ? outputFile : outputFile.resolveSibling(name.substring(0, dot));
    }

    private static void printUsage() {
        System.out.println("""
                license-signer - creates a signed license file (compact JWS, ES256).

                Commands:
                  gen-key
                      generate an EC P-256 signing keypair (base64 PKCS#8 / X.509)

                  sign <license-config.yaml> --key <private-key-file>
                      sign with a key you already hold - the normal vendor path

                  sign <license-config.yaml> --mint-key
                      mint a fresh keypair, sign with it, and write THREE files beside the output:
                      the .jws licence, <name>-private.key and <name>-public.key. Refuses to
                      overwrite an existing key file. What happens to the private half afterwards is
                      the operator's decision - for a test licence it is used once and destroyed,
                      since only the public half is needed to verify.

                The signing key is NEVER named by the recipe; it is a command-line concern.
                The YAML config carries opaque capability keys; no capability closure is computed here.
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

    private static void sign(Path configFile, @Nullable Path keyFileOrNull) throws Exception {
        if (Files.notExists(configFile)) {
            System.out.println("249.020 config file doesn't exist: " + configFile);
            return;
        }
        System.out.println("License config: " + configFile.toAbsolutePath());
        final String yaml = Files.readString(configFile, StandardCharsets.UTF_8);
        System.out.println("License config content:\n" + yaml);
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

        // LicenseClaimsBuilder enforces the mandatory-exp rule: there is no timeless license.
        final LicenseClaims claims;
        try {
            claims = LicenseClaimsBuilder.build(config.license, Instant.now());
        }
        catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            return;
        }

        final ECPrivateKey privateKey;
        Path mintedPrivatePath = null;
        Path mintedPublicPath = null;
        if (keyFileOrNull==null) {
            // Both halves are written. Handing back only the public half would make the tool lossy:
            // whoever misses the console output has lost the signing key with no way to recover it.
            // Whether the private half survives afterwards is the operator's call, not the tool's.
            final Path base = stripSuffix(Path.of(signing.outputFile));
            mintedPrivatePath = base.resolveSibling(base.getFileName() + "-private.key");
            mintedPublicPath = base.resolveSibling(base.getFileName() + "-public.key");
            if (Files.exists(mintedPrivatePath) || Files.exists(mintedPublicPath)) {
                // overwriting a signing key silently would be unrecoverable.
                System.out.println("249.080 key file already exists, refusing to overwrite: "
                        + (Files.exists(mintedPrivatePath) ? mintedPrivatePath : mintedPublicPath));
                return;
            }
            final KeyPair kp = EcP256Keys.generate();
            privateKey = (ECPrivateKey) kp.getPrivate();
            Files.writeString(mintedPrivatePath, EcP256Keys.encodeBase64(kp.getPrivate()), StandardCharsets.UTF_8);
            Files.writeString(mintedPublicPath, EcP256Keys.encodeBase64(kp.getPublic()), StandardCharsets.UTF_8);
        }
        else {
            if (Files.notExists(keyFileOrNull)) {
                System.out.println("249.070 private key file doesn't exist: " + keyFileOrNull);
                return;
            }
            privateKey = EcP256Keys.readPrivateKey(Files.readString(keyFileOrNull, StandardCharsets.UTF_8));
        }
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
        System.out.println("  licensee     : " + claims.licensee);
        System.out.println("  edition      : " + claims.edition);
        System.out.println("  capabilities : " + claims.capabilities);
        System.out.println("  databases    : " + claims.databases);
        System.out.println("  storages     : " + claims.storages);
        System.out.println("  exp          : " + claims.exp);
        System.out.println("\n\nlicense:\n" + jws+"\n\n");
        if (mintedPrivatePath!=null) {
            System.out.println("  private key  : " + mintedPrivatePath.toAbsolutePath());
            System.out.println("  public key   : " + mintedPublicPath.toAbsolutePath());
            System.out.println();
            System.out.println("A fresh signing keypair was minted. NEVER commit the private half; the public half is");
            System.out.println("what the verifier needs. Re-issuing under this key later requires keeping the private");
            System.out.println("half somewhere safe - destroying it makes this licence permanently un-reissuable.");
        }
    }

    /**
     * Encode the claims as a JOSE claims set. Nimbus owns this encoding - iat/nbf/exp are registered
     * claims and must go on the wire as NumericDate - which is why the write path does not run
     * through the JSON chain the verify side reads with. The two agree because every private claim
     * is named exactly as the field it maps to, and the empty deployment lists are written out
     * rather than omitted: an empty allow-list is a grant of nothing and must be legible as such.
     */
    private static String toPayloadJson(LicenseClaims claims) {
        final JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .claim("licensee", claims.licensee)
                .claim("edition", claims.edition)
                .claim("capabilities", claims.capabilities)
                .claim("databases", claims.databases)
                .claim("storages", claims.storages)
                .claim("version", claims.version);
        if (claims.iat!=null) {
            b.issueTime(Date.from(claims.iat));
        }
        if (claims.nbf!=null) {
            b.notBeforeTime(Date.from(claims.nbf));
        }
        if (claims.exp!=null) {
            b.expirationTime(Date.from(claims.exp));
        }
        if (claims.installationId!=null && !claims.installationId.isBlank()) {
            b.claim("installationId", claims.installationId);
        }
        return b.build().toString();
    }
}
