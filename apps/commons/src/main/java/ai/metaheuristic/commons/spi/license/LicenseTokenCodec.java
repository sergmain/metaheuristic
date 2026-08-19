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
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
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
 * key selected by kid, each header failure reporting its OWN state so the reader is sent to the
 * field that actually failed rather than to the signature, the mandatory exp, optional installation_id binding, and the exp/nbf window
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
            LicenseKeyResolver keys,
            Instant now,
            @Nullable String localInstallationId) {

        // ❗ Each failure below reports its OWN state, and the try blocks are scoped so that a
        // throw is attributed to the step that threw. One try around the whole read used to make
        // every failure SIGNATURE_INVALID, including a string that was never a token: the reader
        // was sent to inspect a signature that had not been reached, let alone examined.
        final JWT parsed;
        try {
            parsed = JWTParser.parse(token);
        }
        catch (Exception e) {
            // Not a JOSE token at all - a truncated paste, a path, the body of a PEM.
            return invalid(token, LicenseState.UNPARSEABLE);
        }
        if (parsed instanceof PlainJWT) {
            // alg:none. There is no signature to support, which is not the same thing as a
            // signature made with an algorithm this build does not implement.
            return invalid(token, LicenseState.UNSIGNED_TOKEN);
        }
        if (parsed instanceof EncryptedJWT) {
            // JWE - a deliberate gap in this build, and nothing is wrong with the token.
            return invalid(token, LicenseState.ENCRYPTED_TOKEN);
        }
        if (!(parsed instanceof SignedJWT jwt)) {
            // a JWT container this build has never heard of: nothing here can be read.
            return invalid(token, LicenseState.UNPARSEABLE);
        }
        if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())) {
            return invalid(token, LicenseState.UNSUPPORTED_ALGORITHM);
        }
        final JOSEObjectType typ = jwt.getHeader().getType();
        if (typ == null || !EXPECTED_TYP.equals(typ.getType())) {
            return invalid(token, LicenseState.WRONG_TOKEN_TYPE);
        }
        final String kid = jwt.getHeader().getKeyID();
        if (kid == null || kid.isBlank()) {
            return invalid(token, LicenseState.MISSING_KID);
        }
        final ECPublicKey pub = keys.keyFor(kid);
        if (pub == null) {
            // The signature is NOT examined here. Reporting SIGNATURE_INVALID would name the
            // wrong artifact: a perfectly good signature under a kid nobody configured.
            //
            // ❗ And which of the two refusals it is depends on whether this installation holds
            // any key material. With none configured NOTHING can resolve, so naming the kid would
            // accuse the one part of the token that is beyond reproach; the fault is the missing
            // mh.key-store.license.public-key and the state has to say so.
            return invalid(token, keys.configured()
                    ? LicenseState.UNKNOWN_KID
                    : LicenseState.NO_VERIFICATION_KEY);
        }
        final boolean signatureOk;
        try {
            signatureOk = jwt.verify(new ECDSAVerifier(pub));
        }
        catch (Exception e) {
            // the verifier itself refused the key or the signature bytes: still about the signature.
            return invalid(token, LicenseState.SIGNATURE_INVALID);
        }
        if (!signatureOk) {
            return invalid(token, LicenseState.SIGNATURE_INVALID);
        }
        final JWTClaimsSet claimsSet;
        try {
            claimsSet = jwt.getJWTClaimsSet();
        }
        catch (Exception e) {
            // the signature checked out and the body did not read: that is MALFORMED, and the
            // same rule the toClaims block below already follows.
            return invalid(token, LicenseState.MALFORMED);
        }

        // A well-signed token whose body we cannot read is MALFORMED, not SIGNATURE_INVALID: the
        // signature did check out, and reporting otherwise would send the admin after the wrong fault.
        final LicenseClaims claims;
        try {
            claims = toClaims(claimsSet);
        }
        catch (Exception e) {
            return invalid(token, LicenseState.MALFORMED);
        }

        // exp is mandatory (decision 19). There is no timeless license: an omitted exp used to
        // become a perpetual production grant by accident, and a trial that runs out is re-issued.
        if (claims.exp == null) {
            return result(token, LicenseState.MALFORMED, claims);
        }
        // installation binding (Appendix G): only when both a claim and a local id are present.
        if (claims.installationId != null && !claims.installationId.isBlank()
                && localInstallationId != null && !claims.installationId.equals(localInstallationId)) {
            return result(token, LicenseState.INSTALL_ID_MISMATCH, claims);
        }
        // time window with leeway.
        if (claims.nbf != null && now.isBefore(claims.nbf.minus(LEEWAY))) {
            return result(token, LicenseState.NOT_YET_VALID, claims);
        }
        if (now.isAfter(claims.exp.plus(LEEWAY))) {
            return result(token, LicenseState.EXPIRED, claims);
        }
        return result(token, LicenseState.VALID, claims);
    }

    private static LicenseVerificationResult result(String token, LicenseState state, LicenseClaims claims) {
        final Set<String> keys = new HashSet<>(claims.capabilities);
        return new LicenseVerificationResult(token, state, claims, new ClaimsEntitlements(state, claims.exp, keys));
    }

    private static LicenseVerificationResult invalid(String token, LicenseState state) {
        return new LicenseVerificationResult(token, state, null, ClaimsEntitlements.invalid(state));
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
