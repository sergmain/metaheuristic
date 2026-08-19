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

/**
 * License states (Appendix E). valid() is true only for VALID. has(f) is always false when
 * !valid(). The state is surfaced to the admin UI; gating code depends only on valid()/has().
 *
 * Two of these describe the AGGREGATE rather than one license. DATABASE_NOT_LICENSED and
 * STORAGE_NOT_LICENSED mean that no currently-valid license grants the value this dispatcher is
 * actually running on. A single license that omits the running database is not invalid - it simply
 * contributes nothing on that axis - so the check can only be made once, against the union.
 *
 * SIGNATURE_INVALID means one thing only: the signature did not verify under the key the kid
 * selected. It used to be the answer for every header-level rejection too, which was actively
 * misleading - a license refused for an unknown kid reported a bad signature, sending the reader
 * to inspect key material that was never in question while the actual fault, one word in the
 * header, went unnamed. A state's job is to say what to go and look at, so each cause is named.
 *
 * @author Serge
 */
public enum LicenseState {
    NO_LICENSE,
    VALID,
    EXPIRED,
    NOT_YET_VALID,
    SIGNATURE_INVALID,

    /** The header names a kid no configured key answers to. The signature is never examined. */
    UNKNOWN_KID,
    /**
     * NO key material is configured on this installation, so no kid can resolve and no license can
     * ever verify here. Distinct from UNKNOWN_KID, which says the token names a kid the CONFIGURED
     * key does not answer to: there the fault is in the token or in which key was deployed, here
     * there is no key at all and the token is beside the point. Reporting UNKNOWN_KID for this sent
     * every reader to inspect a kid that was never compared against anything.
     */
    NO_VERIFICATION_KEY,
    /** The header carries no kid at all, so no key can be selected. */
    MISSING_KID,
    /** alg is not ES256 on a token that IS signed. */
    UNSUPPORTED_ALGORITHM,
    /**
     * A PlainJWT: alg:none, no signature present at all. Not an unsupported algorithm - there is
     * nothing to verify, which is a forgery attempt rather than a build limitation, and the two
     * deserve different words.
     */
    UNSIGNED_TOKEN,
    /**
     * A JWE. Encrypted tokens are a deliberate gap in this build, not a defect in the token: the
     * holder has done nothing wrong and nothing about the file needs correcting.
     */
    ENCRYPTED_TOKEN,
    /**
     * The string is not a JOSE token at all - a truncated paste, a file path, the body of a PEM.
     * Parsing never reached a signature, so SIGNATURE_INVALID would name an artifact that was
     * never read.
     */
    UNPARSEABLE,
    /** typ is absent or is not license+jws - a signed token that isn't claiming to be a license. */
    WRONG_TOKEN_TYPE,

    INSTALL_ID_MISMATCH,
    DATABASE_NOT_LICENSED,
    STORAGE_NOT_LICENSED,
    MALFORMED,
    REVOKED,
    GRACE,
    UNAVAILABLE,
    TAMPER_DETECTED
}
