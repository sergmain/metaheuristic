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
 * The one way a gate is written.
 *
 * <p>Exists so that "gate a capability" is a single expression with a single meaning rather than a
 * hand-rolled if/throw repeated at every entry point, where the wording, the error code and — worse
 * — the caching behaviour would drift apart.
 *
 * <p>❗ <b>Re-reads on every call and caches nothing.</b> Validity flips as {@code exp} passes, so a
 * gate that captured a boolean once would keep granting a capability after the licence expired.
 * {@link LicenseSource#current()} is itself the thing that decides how fresh the answer is.
 *
 * <p>❗ <b>Never gate a database or a storage backend with this.</b> Those are checked at verify
 * time against the union of every valid licence and invalidate the whole aggregate — which is
 * exactly why they are modelled as claim fields rather than capabilities. A gate reading a
 * deployment value is a defect.
 *
 * <p>Takes the {@link LicenseSource} as a parameter rather than resolving one: this class carries
 * no policy about what a missing backend means, because that is a deployment decision and not a
 * gating one.
 *
 * <p>Error code prefix: {@code 01.263.} (unique to this class).
 *
 * @author Serge
 */
public class LicenseGuard {

    private LicenseGuard() {
    }

    /** Throws {@link LicenseException} unless the installation is currently entitled to {@code feature}. */
    public static void require(LicenseSource licenseSource, Feature feature) {
        if (!licenseSource.current().has(feature)) {
            throw new LicenseException(
                    "01.263.010 capability is not licensed: " + feature.key(), feature.key());
        }
    }
}
