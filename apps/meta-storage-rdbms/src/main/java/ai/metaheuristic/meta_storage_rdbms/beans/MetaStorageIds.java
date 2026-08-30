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

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * Id-allocation holder, copied from MH's {@code Ids} / {@code mh_gen_ids}.
 *
 * <p>The generator is the reason this exists, not the entity. {@code @TableGenerator} allocates
 * primary keys from a row in {@code META_STORAGE_RECORD_GEN_IDS}, so no column anywhere in this module is
 * IDENTITY, AUTO_INCREMENT or SERIAL. That is what makes the schema behave identically on H2,
 * MySQL, MariaDB and PostgreSQL without a dialect-specific column definition - and it keeps int4
 * SERIAL primary keys, which the Aurora audit flagged on high-churn tables, out of the design.
 *
 * <p>{@code allocationSize = 1} matches MH: one round-trip per insert, no gaps.
 *
 * @author Serge
 */
@Entity
@Table(name = "META_STORAGE_RECORD_IDS")
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
@NoArgsConstructor
public class MetaStorageIds implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "meta_storage_record_ids")
    public Long id;

    public Integer stub;
}
