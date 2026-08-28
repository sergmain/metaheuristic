/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Blob;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 6:56 PM
 */
@Entity
@Table(name = "MH_CACHE_VARIABLE")
@Data
@EqualsAndHashCode(of = {"cacheProcessId", "variableName"})
@ToString
@NoArgsConstructor
public class CacheVariable implements Serializable {
    @Serial
    private static final long serialVersionUID = -1541017259044013865L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

//    @NotNull
    @Column(name = "CACHE_PROCESS_ID")
    public Long cacheProcessId;

    @Column(name="CREATED_ON")
    public long createdOn;

//    @NotNull
//    @NotEmpty
    @Column(name = "VARIABLE_NAME")
    public String variableName;

    @Column(name = "IS_NULLIFIED")
    public boolean nullified;

    /**
     * MH_VARIABLE_BLOB.ID holding this cached output, or null when the cached value is itself null
     * (see IS_NULLIFIED). The payload used to sit in a DATA column on this row; moving it out makes a
     * cached output an ordinary VariableBlob, so it shares the variable storage backends instead of
     * needing a parallel set, and MH_CACHE_VARIABLE stops carrying a large object of its own.
     */
    @Nullable
    @Column(name = "VARIABLE_BLOB_ID")
    public Long variableBlobId;

}
