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

package ai.metaheuristic.meta_storage_rdbms;

import ai.metaheuristic.meta_storage_rdbms.data.MetaRecordParams;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The whole surface of the meta storage. Two methods, and nothing else opens the store.
 *
 * <p><b>Why an SPI at all.</b> External Functions never touch the store - they take input Variables
 * and produce output Variables, and that is their entire contact with the world. A dedicated
 * internal Function calls this interface and materialises the result into a Variable. Because this
 * is the only door, the backing engine is swappable without touching a single {@code .mhsc} or any
 * external Function: the host's own RDBMS here, something else later, same two methods.
 *
 * <p><b>Why exactly two.</b> Filtering, fan-out, parallelism and aggregation are already MH
 * primitives - {@code filter-lines} over a key list, {@code mh.batch-line-splitter} for the
 * fan-out, {@code mh.aggregate} for the gather. The store is asked only to hand records out and
 * take them back; it is not a query engine and must not grow into one through this interface.
 *
 * <p><b>Concurrency.</b> Whatever the engine permits is an implementation detail of the implementing
 * class and is deliberately NOT a concern of the caller - the pipeline author must not have to
 * reason about it. On the host's RDBMS there is nothing to serialise: the same pool, transaction
 * manager and optimistic locking that guard every other write guard these too.
 *
 * @author Serge
 */
public interface MetaStorageSpi {

    /**
     * Read records of one type out of one bucket.
     *
     * <p>Both shapes the pipeline needs are this one call:
     * <ul>
     *   <li>{@code recKeys == null} or empty - every record of that type, which is the "select cold
     *       contacts" step feeding the batch splitter;</li>
     *   <li>{@code recKeys} non-empty - exactly those records, which is the per-batch fetch that
     *       runs once the splitter has handed a task its slice of keys.</li>
     * </ul>
     *
     * <p>Payloads are only ever materialised here. A key list is small enough to fit one Variable
     * at 600k records; payloads are not, which is why they are fetched per batch and never in bulk.
     *
     * @param bucket  tenant/namespace. Opaque to the store.
     * @param type    entity kind. A string, never an enum.
     * @param recKeys natural keys to fetch, or null/empty for all of that type.
     * @return records, ordered by {@code recKey} so a run is reproducible. Never null.
     */
    List<MetaRecordParams> fetch(String bucket, String type, @Nullable List<String> recKeys);

    /**
     * Write records back, insert-or-update on {@code (bucket, type, recKey)}.
     *
     * <p>The natural key is what makes a retried task safe: a replayed batch overwrites the rows it
     * wrote the first time instead of appending duplicates. A store that could not express "this
     * write already happened" would make every effectful task unsafe to retry, which is why the
     * uniqueness lives in the schema rather than in caller discipline.
     *
     * <p>{@code type} and {@code recKey} are read from each record - they are body fields projected
     * into indexed columns, not separate parameters.
     *
     * @param bucket  tenant/namespace. Opaque to the store.
     * @param records records to write. An empty list is a no-op returning 0.
     * @return number of rows written.
     */
    int upsert(String bucket, List<MetaRecordParams> records);
}
