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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

@Entity
@Table(name = "MH_VARIABLE")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@ToString(exclude={"data", "bytes"})
@NoArgsConstructor
public class Variable implements Serializable {
    @Serial
    private static final long serialVersionUID = 7768428475142175426L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "IS_INITED")
    public boolean inited;

    @Column(name = "IS_NULLIFIED")
    public boolean nullified;

    @Column(name = "NAME")
    public String name;

    @NonNull
    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @NonNull
    @Column(name = "TASK_CONTEXT_ID")
    public String taskContextId;

    @Column(name = "UPLOAD_TS")
    public Timestamp uploadTs;

    @Nullable
    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Nullable
    @Column(name = "FILENAME")
    public String filename;

    @Transient
    public byte[] bytes;

    // ai.metaheuristic.api.data_storage.DataStorageParams is here
    @Column(name = "PARAMS")
    public String params;

    // TODO 2020-12-21 need to add a way to check the length of variable with length of stored on disk variable
    //  maybe even with checksum
}
