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

package ai.metaheuristic.api.data.license;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Version-less (current) schema of the JSON payload of a signed license (the JWS body). This is
 * NOT the operator YAML recipe - that is LicenseConfigYaml. Must remain field-for-field identical
 * to the latest versioned class ({@link LicenseClaimsV1}); business logic only ever sees this one.
 *
 * <p>A license carries THREE independent grant fields and nothing else grants anything:
 * <ul>
 *   <li>{@code capabilities} - opaque 'Category:VALUE' key strings, enforced by a gate written at
 *       the capability's entry point. The license manager never learns what any of them mean.</li>
 *   <li>{@code databases} - allow-list of database values this license permits.</li>
 *   <li>{@code storages} - allow-list of storage-backend values this license permits.</li>
 * </ul>
 * Databases and storages are NOT opaque: they are MH's own concepts, so the license manager
 * resolves the running value itself and checks it, with no gate to write and nothing to forget.
 * Capabilities are proprietary vocabulary MH must not name, which is why they can only be enforced
 * from the call site.
 *
 * <p>Grants only: there is no negative claim, no exclusion and no revocation list. An empty
 * allow-list therefore grants NOTHING on that axis - it does not mean 'unconstrained'. An
 * unconstrained value would be an unbounded grant reachable from a single license, and under the
 * union of several licenses one unbounded grant is unbounded for the whole set.
 *
 * <p>Seal: 'capabilities' are stored as data, never as symbols, and an 'edition' is never expanded
 * into a capability closure here - that closure is proprietary and lives off-MH.
 *
 * @author Serge
 */
@Data
public class LicenseClaims implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    public String licensee;

    // display/reporting claim only; never mapped to capabilities inside MH.
    public String edition;

    // opaque 'Category:VALUE' key strings, verbatim.
    public List<String> capabilities = new ArrayList<>();

    // allow-list of database values; empty grants no database at all.
    public List<String> databases = new ArrayList<>();

    // allow-list of storage-backend values; empty grants no storage at all.
    public List<String> storages = new ArrayList<>();

    @Nullable
    public Instant iat;

    @Nullable
    public Instant nbf;

    /**
     * REQUIRED. There is no timeless license: a token without exp is rejected at verify as
     * MALFORMED, and a trial that runs out is re-issued rather than extended.
     *
     * <p>Nullable in the DTO on purpose. Making the absence of exp a checkIntegrity() failure
     * would turn a malformed token into a thrown exception during parsing, which the verify path
     * can only report as SIGNATURE_INVALID - a wrong and misleading answer. The requirement is
     * enforced where it can be reported honestly: at issuance (LicenseClaimsBuilder) and at verify
     * (LicenseTokenCodec, which answers MALFORMED).
     */
    @Nullable
    public Instant exp;

    @Nullable
    public String installationId;

    @Override
    public int getVersion() {
        return version;
    }
}
