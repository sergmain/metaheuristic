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

package ai.metaheuristic.meta_storage_rdbms.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * One meta-storage record on the MAIN datasource - the JPA-backed twin of {@code META_RECORD} in
 * the SQLite implementation, with identical semantics.
 *
 * <p>{@link #body} is the system of record. {@link #type} and {@link #recKey} are body fields
 * projected into indexed columns; drop them and they can be recomputed from the bodies.
 * {@code (BUCKET, TYPE, REC_KEY)} is the natural key, which is what makes a replayed batch idempotent
 * rather than duplicating.
 *
 * <p>{@link #type} is a column VALUE, never an enum: a new entity kind is a new string and needs no
 * DDL, no recompile and no restart.
 *
 * <p>❗ The id comes from {@code @TableGenerator}, not IDENTITY. Hibernate ships a dialect for every
 * engine this module supports, so entity and JPQL are written once and Hibernate emits whatever each
 * engine needs - which is the portability that hand-written SQL against a single embedded engine has
 * to reimplement.
 *
 * @author Serge
 */
@Entity
@Table(name = "META_STORAGE_RECORD")
@Data
@TableGenerator(
        name = "meta_storage_record_ids",
        table = "META_STORAGE_RECORD_GEN_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "meta_storage_record_ids",
        allocationSize = 1,
        initialValue = 1
)
public class MetaStorageRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "meta_storage_record_ids")
    @Column(name = "ID")
    public Long id;

    /** Optimistic locking, at the grain that actually changes - one record, one row. */
    @Version
    @Column(name = "VERSION")
    public Integer version;

    @Column(name = "BUCKET")
    public String bucket;

    @Column(name = "TYPE")
    public String type;

    @Column(name = "REC_KEY")
    public String recKey;

    /** Versioned JSON. Assumed to stay under 16k, so TEXT on every dialect and never a BLOB. */
    @Column(name = "BODY")
    public String body;

    /**
     * Monotonic stamp. Under mutation an index entry can be STALE rather than merely missing, and
     * this is what lets a late arrival be discarded. It is also the value a {@code .mhsc} feeds into
     * the cache-busting slot, so a cached key list is invalidated by the store's own state instead
     * of by an operator remembering to bump a seed.
     */
    @Column(name = "GEN")
    public long gen;

    @Column(name = "UPDATED_AT")
    public long updatedAt;
}
