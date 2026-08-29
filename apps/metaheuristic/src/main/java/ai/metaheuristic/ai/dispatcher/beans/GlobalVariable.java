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
import java.sql.Timestamp;

@Entity
@Table(name = "MH_VARIABLE_GLOBAL")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@ToString
@NoArgsConstructor
public class GlobalVariable implements Serializable {
    @Serial
    private static final long serialVersionUID = 5114121077582180465L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "NAME")
    public String name;

    @Column(name = "UPLOAD_TS")
    public Timestamp uploadTs;

    /**
     * MH_VARIABLE_BLOB.ID holding this global variable's payload, or null when it has none - a global
     * variable declared with external storage carries its location in PARAMS and no bytes at all.
     * The payload used to sit in a DATA column on this row; on PostgreSQL that was the last OID
     * besides MH_VARIABLE_BLOB's own.
     */
    @Nullable
    @Column(name = "VARIABLE_BLOB_ID")
    public Long variableBlobId;

    @Nullable
    @Column(name = "FILENAME")
    public String filename;

    @Column(name = "PARAMS")
    public String params;

}
