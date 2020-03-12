/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
    private static final long serialVersionUID = 7768428475142175426L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "IS_INITED")
    public boolean inited;

    @Column(name = "NAME")
    private String name;

    /**
     * This field is initialized only for data resources which were produced while processing execContext.
     * The data resource which is using as input resources must not have execContextId.
     * Also this field is used as refId for deleting any resources which were produced
     * while execContext was processed.
     */
    @Nullable
    @Column(name = "EXEC_CONTEXT_ID")
    private Long execContextId;

    @Nullable
    @Column(name = "CONTEXT_ID")
    private String contextId;

    @Column(name = "UPLOAD_TS")
    private Timestamp uploadTs;

    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Nullable
    @Column(name = "FILENAME")
    public String filename;

    @Transient
    public byte[] bytes;

    @Column(name = "PARAMS")
    public String params;

}
