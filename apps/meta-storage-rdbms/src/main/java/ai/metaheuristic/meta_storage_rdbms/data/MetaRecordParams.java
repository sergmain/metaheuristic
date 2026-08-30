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

package ai.metaheuristic.meta_storage_rdbms.data;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Version-less (current) schema of {@code META_RECORD.BODY} - one record in the meta storage.
 * Must remain field-for-field identical to the latest versioned class
 * ({@link MetaRecordParamsV1}); business logic only ever sees this one.
 *
 * <p>{@link #type} is the entity kind and it is a STRING, not an enum. That is the whole point of the
 * store: a new kind of thing is a new value, never a new table, a new column, a recompile or a restart.
 * The name is the type.
 *
 * <p>{@link #type} and {@link #recKey} are duplicated into indexed columns by the storage layer. The
 * body stays the system of record; the columns are a projection of it and can be rebuilt from it.
 *
 * <p>The remaining fields carry the crawled-contact shape used by the prototype. In the real store a
 * caller-defined payload lives here instead, and adding a {@code @Nullable} field to BOTH this class
 * and the highest-numbered versioned class needs no version bump.
 *
 * @author Serge
 */
@Data
public class MetaRecordParams implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    /** Entity kind. Projected to {@code META_RECORD.TYPE}. Never an enum. */
    public String type;

    /**
     * Natural key, unique within (bucket, type). This IS the idempotency key: a replayed batch
     * upserts onto the same row instead of appending a duplicate.
     */
    public String recKey;

    public String name;

    @Nullable
    public String secondName;

    @Nullable
    public String email;

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public boolean checkIntegrity() {
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("01.943.020 type is blank");
        }
        if (recKey == null || recKey.isBlank()) {
            throw new IllegalStateException("01.943.040 recKey is blank");
        }
        return true;
    }
}
