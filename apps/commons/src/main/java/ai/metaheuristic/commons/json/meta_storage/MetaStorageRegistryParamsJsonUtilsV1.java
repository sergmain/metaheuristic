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
package ai.metaheuristic.commons.json.meta_storage;

import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParamsV1;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import tools.jackson.core.JacksonException;
import org.jspecify.annotations.NonNull;

/**
 * V1 of the {@code MH_META_STORAGE_REGISTRY.PARAMS} chain, and currently its head: it upgrades V1
 * straight to the version-less class, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.944.} (unique to this class).
 *
 * @author Serge
 */
public class MetaStorageRegistryParamsJsonUtilsV1
    extends AbstractParamsJsonUtils<MetaStorageRegistryParamsV1, MetaStorageRegistryParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public MetaStorageRegistryParams upgradeTo(@NonNull MetaStorageRegistryParamsV1 src) {
        src.checkIntegrity();
        MetaStorageRegistryParams trg = new MetaStorageRegistryParams();
        trg.desc = src.desc;
        trg.producer = src.producer;
        trg.recKeyFormat = src.recKeyFormat;
        trg.bodyFormat = src.bodyFormat;
        trg.execContextId = src.execContextId;
        trg.function = src.function;
        trg.consumer = src.consumer;
        trg.checkIntegrity();
        return trg;
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
    public Void downgradeTo(@NonNull Void unused) {
        throw new DowngradeNotSupportedException();
    }

    @NonNull
    @Override
    public MetaStorageRegistryParamsV1 to(@NonNull String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, MetaStorageRegistryParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.944.020 Error: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public String toString(@NonNull MetaStorageRegistryParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.944.040 Error: " + e.getMessage(), e);
        }
    }
}