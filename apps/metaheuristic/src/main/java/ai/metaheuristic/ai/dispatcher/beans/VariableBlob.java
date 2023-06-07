/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Blob;

@Entity
@Table(name = "MH_VARIABLE_BLOB")
@Data
@NoArgsConstructor
public class VariableBlob implements Serializable {
    @Serial
    private static final long serialVersionUID = 4581156711587738626L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @NonNull
    @Column(name = "VARIABLE_ID")
    public Long variableId;

    @Nullable
    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Transient
    public byte[] bytes;

    // TODO 2020-12-21 need to add a way to check the length of variable with length of stored on disk variable
    //  maybe even with checksum
}
