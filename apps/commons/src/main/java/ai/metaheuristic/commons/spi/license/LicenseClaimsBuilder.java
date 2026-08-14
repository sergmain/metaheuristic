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
import ai.metaheuristic.api.data.license.LicenseConfigYaml;
import ai.metaheuristic.commons.S;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, Spring-less resolution of an operator {@link LicenseConfigYaml.License} recipe into a
 * signed-ready {@link LicenseClaims}. Generic and proprietary-free: 'capabilities' pass through as
 * opaque strings, and 'edition' is never expanded into a capability closure here.
 *
 * Resolution: iat = now; exp = expiresAt OR (iat + validityDuration); nbf = notBefore.
 *
 * exp is mandatory. There is no timeless license: a recipe that names neither an absolute instant
 * nor a duration is rejected at issuance rather than producing a token that never stops granting.
 * A trial that runs out is re-issued - signing is one command - and the deployment axes, not an
 * absent exp, are what keep a trial off a production datastore.
 *
 * Capability strings are copied verbatim: no shape check and no vocabulary check. Which capability
 * names exist is proprietary knowledge that never reaches this class; a typo'd name is a valid
 * license granting nothing.
 *
 * 'databases' and 'storages' are not capabilities: they name MH's own deployment concepts, they are
 * checked against the running deployment rather than gated, and they live in their own claim fields.
 *
 * <p>Error code prefix: {@code 01.248.} (unique to this class).
 *
 * @author Serge
 */
public class LicenseClaimsBuilder {

    private LicenseClaimsBuilder() {
    }

    public static LicenseClaims build(LicenseConfigYaml.License lic, Instant now) {
        if (S.b(lic.licensee)) {
            throw new IllegalStateException("248.030 'licensee' must not be blank");
        }
        if (S.b(lic.edition)) {
            throw new IllegalStateException("248.040 'edition' must not be blank");
        }

        final boolean hasExpiresAt = !S.b(lic.expiresAt);
        final boolean hasDuration = !S.b(lic.validityDuration);
        if (hasExpiresAt && hasDuration) {
            throw new IllegalStateException("248.010 'expiresAt' and 'validityDuration' are mutually exclusive");
        }

        Instant exp = null;
        if (hasExpiresAt) {
            exp = Instant.parse(lic.expiresAt);
        }
        else if (hasDuration) {
            exp = now.plus(Duration.parse(lic.validityDuration));
        }

        // exp is mandatory (decision 19): an omitted exp used to become a perpetual production
        // license by accident, and that whole class of bug goes away by refusing to sign one.
        if (exp==null) {
            throw new IllegalStateException("01.248.020 a license MUST declare either 'expiresAt' or 'validityDuration'");
        }

        Instant nbf = null;
        if (!S.b(lic.notBefore)) {
            nbf = Instant.parse(lic.notBefore);
        }

        final LicenseClaims claims = new LicenseClaims();
        claims.licensee = lic.licensee;
        claims.edition = lic.edition;
        claims.capabilities = toCapabilityNames(lic.capabilities);
        claims.databases = toPlainValues(lic.databases);
        claims.storages = toPlainValues(lic.storages);
        claims.iat = now;
        claims.nbf = nbf;
        claims.exp = exp;
        claims.installationId = lic.installationId;
        return claims;
    }

    /**
     * Capabilities are opaque names, copied verbatim.
     *
     * <p>UPDATE: this used to check each entry against a wire-form grammar and rebuild it from the
     * pieces the grammar found. There is no grammar - a capability is one name, and the issuer has
     * no opinion about what a name may look like. Each entry still goes through Feature, which is
     * now a single non-blank rule.
     */
    private static List<String> toCapabilityNames(@Nullable List<String> capabilities) {
        final List<String> names = new ArrayList<>();
        if (capabilities==null) {
            return names;
        }
        for (String f : capabilities) {
            if (S.b(f)) {
                throw new IllegalStateException("01.248.050 'capabilities' must not contain a blank entry");
            }
            names.add(new Feature(f).name());
        }
        return names;
    }

    /**
     * A deployment axis is an allow-list of bare values. An empty list is legal and grants nothing
     * on that axis - it is not 'unconstrained' - so only blank entries are rejected.
     */
    private static List<String> toPlainValues(@Nullable List<String> values) {
        final List<String> out = new ArrayList<>();
        if (values==null) {
            return out;
        }
        for (String v : values) {
            if (S.b(v)) {
                throw new IllegalStateException("01.248.070 'databases' and 'storages' must not contain a blank entry");
            }
            out.add(v);
        }
        return out;
    }
}
