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

package ai.metaheuristic.commons.spi.license;

import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.commons.json.license.LicenseClaimsUtils;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;

import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Verify side of ONE license token. Nimbus-direct (no Spring Security OAuth2 decoder),
 * container-aware: JWTParser dispatches, and only a SignedJWT (compact JWS) is accepted today - a
 * PlainJWT (alg:none) or an EncryptedJWT (JWE, future) is rejected structurally, before any claim
 * is trusted.
 *
 * Enforces the header contract (ES256, typ=license+jws, kid present), signature over the EC public
 * key selected by kid, the mandatory exp, optional installation_id binding, and the exp/nbf window
 * with +-60s leeway (Appendix F). Pure and Spring-less: the clock, install id and key resolver are
 * all parameters.
 *
 * Deliberately NOT checked here: the running database and storage. Those are properties of the
 * installation, not of a token, and are decided once against the union of every valid license
 * (LicenseUnionUtils) - a license that does not list the running database is not invalid, it simply
 * does not contribute that database.
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
            @Nullable String localInstallationId) {

        final JWTClaimsSet claimsSet;
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
            claimsSet = jwt.getJWTClaimsSet();
        }
        catch (Exception e) {
            return invalid(LicenseState.SIGNATURE_INVALID);
        }

        // A well-signed token whose body we cannot read is MALFORMED, not SIGNATURE_INVALID: the
        // signature did check out, and reporting otherwise would send the admin after the wrong fault.
        final LicenseClaims claims;
        try {
            claims = toClaims(claimsSet);
        }
        catch (Exception e) {
            return invalid(LicenseState.MALFORMED);
        }

        // exp is mandatory (decision 19). There is no timeless license: an omitted exp used to
        // become a perpetual production grant by accident, and a trial that runs out is re-issued.
        if (claims.exp == null) {
            return result(LicenseState.MALFORMED, claims);
        }
        // installation binding (Appendix G): only when both a claim and a local id are present.
        if (claims.installationId != null && !claims.installationId.isBlank()
                && localInstallationId != null && !claims.installationId.equals(localInstallationId)) {
            return result(LicenseState.INSTALL_ID_MISMATCH, claims);
        }
        // time window with leeway.
        if (claims.nbf != null && now.isBefore(claims.nbf.minus(LEEWAY))) {
            return result(LicenseState.NOT_YET_VALID, claims);
        }
        if (now.isAfter(claims.exp.plus(LEEWAY))) {
            return result(LicenseState.EXPIRED, claims);
        }
        return result(LicenseState.VALID, claims);
    }

    private static LicenseVerificationResult result(LicenseState state, LicenseClaims claims) {
        final Set<String> keys = new HashSet<>(claims.capabilities);
        return new LicenseVerificationResult(state, claims, new ClaimsEntitlements(state, claims.exp, keys));
    }

    private static LicenseVerificationResult invalid(LicenseState state) {
        return new LicenseVerificationResult(state, null, ClaimsEntitlements.invalid(state));
    }

    /**
     * The one place the claims chain is entered on the read path. The payload is a JOSE claims set,
     * so by the time the signature has been checked the raw JSON is behind a JWTClaimsSet and the
     * version detector has nothing to sniff; the version claim is read here and named explicitly.
     */
    private static LicenseClaims toClaims(JWTClaimsSet cs) {
        final Object ver = cs.getClaim("version");
        final int version = ver instanceof Number n ? n.intValue() : 1;
        return LicenseClaimsUtils.fromJson(version, cs.toString());
    }
}
