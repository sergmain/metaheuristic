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

package ai.metaheuristic.ai.dispatcher.beans;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * One record in the meta storage - a runtime-typed, schema-less store for data owned by a third
 * party.
 *
 * <p>❗ {@link #body} is OPAQUE to MH. It is stored and returned as a string and is never parsed
 * here: its encoding (JSON, a delimited line, anything else) and its structure are the caller's
 * decision, exactly as the schema of a Lucene document is fully determined by the caller in
 * {@code LuceneIndexService}. MH knows how to store and address a record; it knows nothing about
 * what one means.
 *
 * <p>{@link #type} is a column VALUE, never an enum. A new kind of thing is a new string - no DDL,
 * no recompile, no restart. The name is the type.
 *
 * <p>{@code (COMPANY_ID, TYPE, REC_KEY)} is the natural key. That is what makes a replayed batch
 * idempotent: an upsert lands on the row it wrote the first time instead of appending a duplicate.
 * A write from inside a task is an irreversible effect, so the correctness property needed is
 * idempotency, and the uniqueness lives in the schema rather than in caller discipline.
 *
 * @author Serge
 */
@Entity
@Table(name = "MH_META_STORAGE_SYNTHETIC")
@Data
@TableGenerator(
        name = "mh_meta_storage_synthetic_ids",
        table = "mh_gen_ids",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "mh_meta_storage_synthetic_ids",
        allocationSize = 1,
        initialValue = 1
)
public class MetaStorageSynthetic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "mh_meta_storage_synthetic_ids")
    @Column(name = "ID")
    public Long id;

    /** Optimistic locking at the grain that actually changes - one record, one row. */
    @Version
    @Column(name = "VERSION")
    public Integer version;

    @Column(name = "COMPANY_ID")
    public Long companyId;

    /** Entity kind. Opaque to MH. */
    @Column(name = "TYPE")
    public String type;

    /** Natural key within (companyId, type). Opaque to MH. */
    @Column(name = "REC_KEY")
    public String recKey;

    /** ❗ Opaque payload. MH never parses it. */
    @Column(name = "BODY")
    public String body;

    /**
     * Monotonic change stamp.
     *
     * <p>Distinct from {@link #version}: {@code VERSION} is Hibernate's optimistic lock, scoped to
     * one row inside one transaction. {@code GEN} tells things OUTSIDE the transaction - caches,
     * async indexers, a running pipeline - that what they hold is stale. It is the value a
     * {@code .mhsc} feeds into a cache-busting slot so invalidation follows the store's own state
     * rather than an operator remembering to bump a seed.
     */
    @Column(name = "GEN")
    public long gen;

    @Column(name = "UPDATED_AT")
    public long updatedAt;
}
