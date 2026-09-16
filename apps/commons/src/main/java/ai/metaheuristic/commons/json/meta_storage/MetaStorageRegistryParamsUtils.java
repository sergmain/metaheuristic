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
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;

import java.util.Map;

/**
 * Registry of the {@code MH_META_STORAGE_REGISTRY.PARAMS} version chain.
 *
 * <p>Read with {@code BASE_JSON_UTILS.to(json)} - it detects the stored version and upgrades through
 * the chain, so callers only ever hold {@link MetaStorageRegistryParams}. Write with
 * {@code BASE_JSON_UTILS.toString(params)}, which always emits the latest version.
 *
 * @author Serge
 */
public class MetaStorageRegistryParamsUtils {

    private static final MetaStorageRegistryParamsJsonUtilsV1 UTILS_V_1 = new MetaStorageRegistryParamsJsonUtilsV1();
    private static final MetaStorageRegistryParamsJsonUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final BaseJsonUtils<MetaStorageRegistryParams> BASE_JSON_UTILS =
        new BaseJsonUtils<>(
            (Map) Map.of(
                1, (AbstractParamsJsonUtils) UTILS_V_1
            ),
            (AbstractParamsJsonUtils) DEFAULT_UTILS
        );
}