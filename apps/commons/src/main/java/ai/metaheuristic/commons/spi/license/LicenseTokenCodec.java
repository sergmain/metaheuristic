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

package ai.metaheuristic.commons.spi.license;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;

import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Verify side of the license token. Nimbus-direct (no Spring Security OAuth2 decoder), container-aware:
 * JWTParser dispatches, and only a SignedJWT (compact JWS) is accepted today - a PlainJWT (alg:none)
 * or an EncryptedJWT (JWE, future) is rejected structurally, before any claim is trusted.
 *
 * Enforces the header contract (ES256, typ=license+jws, kid present), signature over the EC public key
 * selected by kid, the timeless-requires-required_profiles safety rule, the deployment profile
 * constraint, optional installation_id binding, and the exp/nbf window with +-60s leeway (Appendix F).
 * Pure and Spring-less: the clock, active profiles, install id, and key resolver are all parameters.
 *
 * @author Serge
 */
public class LicenseTokenCodec {

    public static final Duration LEEWAY = Duration.ofSeconds(60);
    private static final String EXPECTED_TYP = "license+jws";

    private LicenseTokenCodec() {
    }

    public static LicenseVerificationResult verify(
            String token,
            Function<String, @Nullable ECPublicKey> keyByKid,
            Instant now,
            Set<String> activeProfiles,
            @Nullable String localInstallationId) {
        try {
            final JWT parsed = JWTParser.parse(token);
            if (!(parsed instanceof SignedJWT jwt)) {
                // PlainJWT (alg:none) or EncryptedJWT (JWE - not supported yet) -> reject
                return invalid(LicenseState.SIGNATURE_INVALID);
            }
            if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())) {
                return invalid(LicenseState.SIGNATURE_INVALID);
            }
            final JOSEObjectType typ = jwt.getHeader().getType();
            if (typ == null || !EXPECTED_TYP.equals(typ.getType())) {
                return invalid(LicenseState.SIGNATURE_INVALID);
            }
            final String kid = jwt.getHeader().getKeyID();
            if (kid == null || kid.isBlank()) {
                return invalid(LicenseState.SIGNATURE_INVALID);
            }
            final ECPublicKey pub = keyByKid.apply(kid);
            if (pub == null) {
                return invalid(LicenseState.SIGNATURE_INVALID);
            }
            if (!jwt.verify(new ECDSAVerifier(pub))) {
                return invalid(LicenseState.SIGNATURE_INVALID);
            }

            final LicenseClaimsV1 claims = toClaims(jwt.getJWTClaimsSet());

            // Appendix D safety MUST: no exp is accepted only when deployment-pinned.
            if (claims.exp == null && claims.requiredProfiles.isEmpty()) {
                return result(LicenseState.MALFORMED, claims);
            }
            // installation binding (Appendix G): only when both a claim and a local id are present.
            if (claims.installationId != null && !claims.installationId.isBlank()
                    && localInstallationId != null && !claims.installationId.equals(localInstallationId)) {
                return result(LicenseState.INSTALL_ID_MISMATCH, claims);
            }
            // deployment profile constraint (section 7.7).
            if (!claims.requiredProfiles.isEmpty() && !activeProfiles.containsAll(claims.requiredProfiles)) {
                return result(LicenseState.PROFILE_CONSTRAINT_VIOLATED, claims);
            }
            for (String forbidden : claims.forbiddenProfiles) {
                if (activeProfiles.contains(forbidden)) {
                    return result(LicenseState.PROFILE_CONSTRAINT_VIOLATED, claims);
                }
            }
            // time window with leeway.
            if (claims.nbf != null && now.isBefore(claims.nbf.minus(LEEWAY))) {
                return result(LicenseState.NOT_YET_VALID, claims);
            }
            if (claims.exp != null && now.isAfter(claims.exp.plus(LEEWAY))) {
                return result(LicenseState.EXPIRED, claims);
            }
            return result(LicenseState.VALID, claims);
        }
        catch (Exception e) {
            return invalid(LicenseState.SIGNATURE_INVALID);
        }
    }

    private static LicenseVerificationResult result(LicenseState state, LicenseClaimsV1 claims) {
        final Set<String> keys = new HashSet<>(claims.features);
        return new LicenseVerificationResult(state, claims, new ClaimsEntitlements(state, claims.exp, keys));
    }

    private static LicenseVerificationResult invalid(LicenseState state) {
        return new LicenseVerificationResult(state, null, ClaimsEntitlements.invalid(state));
    }

    private static LicenseClaimsV1 toClaims(JWTClaimsSet cs) throws ParseException {
        final LicenseClaimsV1 c = new LicenseClaimsV1();
        final Object ver = cs.getClaim("ver");
        c.ver = ver instanceof Number n ? n.intValue() : 1;
        c.licensee = cs.getStringClaim("licensee");
        c.edition = cs.getStringClaim("edition");
        c.features = stringList(cs, "features");
        final Date iat = cs.getIssueTime();
        final Date nbf = cs.getNotBeforeTime();
        final Date exp = cs.getExpirationTime();
        c.iat = iat == null ? null : iat.toInstant();
        c.nbf = nbf == null ? null : nbf.toInstant();
        c.exp = exp == null ? null : exp.toInstant();
        c.requiredProfiles = stringList(cs, "required_profiles");
        c.forbiddenProfiles = stringList(cs, "forbidden_profiles");
        c.installationId = cs.getStringClaim("installation_id");
        return c;
    }

    private static List<String> stringList(JWTClaimsSet cs, String name) throws ParseException {
        final List<String> v = cs.getStringListClaim(name);
        return v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
}
