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

package ai.metaheuristic.meta_storage_rdbms.json;

import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import ai.metaheuristic.meta_storage_rdbms.data.MetaRecordParams;

import java.util.Map;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Registry for the JSON multi-versioning chain backing {@code META_RECORD.BODY}.
 *
 * <p>This is the mechanism that makes a schema change a RUNTIME decision rather than a compile-time
 * one: a row written under an older version is upgraded through the chain on read, so the stored
 * body and the class the caller sees may legitimately differ in shape.
 *
 * <p>Callers use {@code BASE_JSON_UTILS.to(json)} to read and
 * {@code BASE_JSON_UTILS.toString(params)} to write.
 *
 * @author Serge
 */
public class MetaRecordParamsUtils {

    private static final MetaRecordParamsJsonUtilsV1 UTILS_V_1 = new MetaRecordParamsJsonUtilsV1();
    private static final MetaRecordParamsJsonUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final BaseJsonUtils<MetaRecordParams> BASE_JSON_UTILS =
        new BaseJsonUtils<>(
            (Map) Map.of(
                1, (AbstractParamsJsonUtils) UTILS_V_1
            ),
            (AbstractParamsJsonUtils) DEFAULT_UTILS
        );
}
