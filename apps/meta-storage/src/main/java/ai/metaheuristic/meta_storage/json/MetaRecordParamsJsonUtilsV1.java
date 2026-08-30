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

package ai.metaheuristic.meta_storage.json;

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import ai.metaheuristic.meta_storage.data.MetaRecordParams;
import ai.metaheuristic.meta_storage.data.MetaRecordParamsV1;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;

/**
 * v1 JSON utilities for {@link MetaRecordParamsV1}.
 *
 * <p>Head of the chain at the moment: {@link #upgradeTo} maps the frozen V1 payload to the
 * version-less {@link MetaRecordParams} by explicit field-by-field mapping. {@link #nextUtil}
 * returns {@code null} since V1 is currently latest.
 *
 * <p>Error code prefix: {@code 01.942.} (unique to this class).
 *
 * @author Serge
 */
public class MetaRecordParamsJsonUtilsV1
        extends AbstractParamsJsonUtils<MetaRecordParamsV1, MetaRecordParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public MetaRecordParams upgradeTo(@NonNull MetaRecordParamsV1 src) {
        src.checkIntegrity();
        final MetaRecordParams trg = new MetaRecordParams();
        trg.type = src.type;
        trg.recKey = src.recKey;
        trg.name = src.name;
        trg.secondName = src.secondName;
        trg.email = src.email;
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
    public String toString(@NonNull MetaRecordParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.942.020 Error: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public MetaRecordParamsV1 to(@NonNull String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, MetaRecordParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.942.040 Error: " + e.getMessage(), e);
        }
    }
}
