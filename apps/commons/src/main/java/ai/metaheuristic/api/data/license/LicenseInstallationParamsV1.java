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

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Frozen v1 schema of {@code MH_LICENSE_INSTALLATION.PARAMS}. Do not modify after release: this
 * class is a deserialization target inside the version chain and nothing else. Business logic works
 * with the version-less {@link LicenseInstallationParams}.
 *
 * <p>Field-for-field identical to {@link LicenseInstallationParams} while V1 is the head of the chain.
 */
@Data
public class LicenseInstallationParamsV1 implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    public String installationId;

    public long createdOn;

    @Override
    public int getVersion() {
        return version;
    }
}
