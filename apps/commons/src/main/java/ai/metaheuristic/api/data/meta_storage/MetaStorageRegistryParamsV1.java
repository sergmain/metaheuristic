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
package ai.metaheuristic.api.data.meta_storage;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ metaheuristic.wiki/p/multi-versioning-mechanic.md</b>
 * <br/>
 * Frozen v1 schema of {@code MH_META_STORAGE_REGISTRY.PARAMS}. Do not modify after release: this class
 * is a deserialization target inside the version chain and nothing else. Business logic works with the
 * version-less {@link MetaStorageRegistryParams}, which
 * {@code MetaStorageRegistryParamsJsonUtilsV1.upgradeTo} produces.
 *
 * <p>Field-for-field identical to {@link MetaStorageRegistryParams} while V1 is the head of the chain.
 *
 * @author Serge
 */
@Data
@NoArgsConstructor
public class MetaStorageRegistryParamsV1 implements BaseParams {

    public final int version = 1;

    public String desc;

    public String producer;

    public String recKeyFormat;

    public String bodyFormat;

    @Nullable
    public Long execContextId;

    @Nullable
    public String function;

    @Nullable
    public String consumer;
}