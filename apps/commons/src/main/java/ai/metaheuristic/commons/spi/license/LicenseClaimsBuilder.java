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

import ai.metaheuristic.api.data.license.LicenseConfigYaml;
import ai.metaheuristic.commons.S;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, Spring-less resolution of an operator {@link LicenseConfigYaml.License} recipe into a
 * signed-ready {@link LicenseClaimsV1}. Generic and proprietary-free: 'features' pass through as
 * opaque strings, and 'edition' is never expanded into a feature closure here.
 *
 * Resolution: iat = now; exp = expiresAt OR (iat + validityDuration) OR none; nbf = notBefore.
 * Enforces the Appendix D safety rule that a timeless (no-exp) license must be deployment-pinned.
 *
 * Feature strings are checked for the 'Category:VALUE' wire form only - a shape check, not a
 * vocabulary check. Which categories and values exist is proprietary knowledge that never
 * reaches this class; a typo'd category is a valid license granting nothing.
 *
 * <p>Error code prefix: {@code 01.248.} (unique to this class).
 *
 * @author Serge
 */
public class LicenseClaimsBuilder {

    private LicenseClaimsBuilder() {
    }

    public static LicenseClaimsV1 build(LicenseConfigYaml.License lic, Instant now) {
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

        final List<String> requiredProfiles = lic.requiredProfiles==null ? new ArrayList<>() : new ArrayList<>(lic.requiredProfiles);

        Instant exp = null;
        if (hasExpiresAt) {
            exp = Instant.parse(lic.expiresAt);
        }
        else if (hasDuration) {
            exp = now.plus(Duration.parse(lic.validityDuration));
        }

        // Appendix D safety MUST: a timeless (no exp) license is accepted only when deployment-pinned,
        // otherwise an omitted exp silently becomes a perpetual production license.
        if (exp==null && requiredProfiles.isEmpty()) {
            throw new IllegalStateException("248.020 a license without 'exp' MUST declare a non-empty 'requiredProfiles'");
        }

        Instant nbf = null;
        if (!S.b(lic.notBefore)) {
            nbf = Instant.parse(lic.notBefore);
        }

        final LicenseClaimsV1 claims = new LicenseClaimsV1();
        claims.ver = 1;
        claims.licensee = lic.licensee;
        claims.edition = lic.edition;
        claims.features = toFeatureKeys(lic.features);
        claims.iat = now;
        claims.nbf = nbf;
        claims.exp = exp;
        claims.requiredProfiles = requiredProfiles;
        claims.forbiddenProfiles = lic.forbiddenProfiles==null ? new ArrayList<>() : new ArrayList<>(lic.forbiddenProfiles);
        claims.installationId = lic.installationId;
        return claims;
    }

    /**
     * Shape check of the 'Category:VALUE' wire form. Rebuilding each entry through Feature reuses the
     * one definition of a well-formed key instead of duplicating the rule here.
     */
    private static List<String> toFeatureKeys(@Nullable List<String> features) {
        final List<String> keys = new ArrayList<>();
        if (features==null) {
            return keys;
        }
        for (String f : features) {
            if (S.b(f)) {
                throw new IllegalStateException("01.248.050 'features' must not contain a blank entry");
            }
            final int idx = f.indexOf(Feature.SEPARATOR);
            if (idx<1 || idx==f.length()-1) {
                throw new IllegalStateException(
                        "01.248.060 feature must be in the form 'Category" + Feature.SEPARATOR + "VALUE', actual: " + f);
            }
            keys.add(new Feature(f.substring(0, idx), f.substring(idx + Feature.SEPARATOR.length())).key());
        }
        return keys;
    }
}
