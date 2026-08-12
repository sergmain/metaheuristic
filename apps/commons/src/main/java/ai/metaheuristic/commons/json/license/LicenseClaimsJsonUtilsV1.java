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

package ai.metaheuristic.commons.json.license;

import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.api.data.license.LicenseClaimsV1;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;

/**
 * v1 JSON utilities for {@link LicenseClaimsV1}.
 *
 * <p>Head of the chain at the moment: {@link #upgradeTo} maps the frozen V1 payload to the
 * version-less {@link LicenseClaims} by explicit field-by-field mapping. {@link #nextUtil} returns
 * {@code null} since V1 is currently latest.
 *
 * @author Serge
 */
public class LicenseClaimsJsonUtilsV1
        extends AbstractParamsJsonUtils<LicenseClaimsV1, LicenseClaims, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public LicenseClaims upgradeTo(@NonNull LicenseClaimsV1 src) {
        src.checkIntegrity();
        final LicenseClaims trg = new LicenseClaims();
        trg.licensee = src.licensee;
        trg.edition = src.edition;
        trg.capabilities = new ArrayList<>(src.capabilities);
        trg.databases = new ArrayList<>(src.databases);
        trg.storages = new ArrayList<>(src.storages);
        trg.iat = src.iat;
        trg.nbf = src.nbf;
        trg.exp = src.exp;
        trg.installationId = src.installationId;
        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void unused) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull LicenseClaimsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public LicenseClaimsV1 to(@NonNull String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, LicenseClaimsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }
}
