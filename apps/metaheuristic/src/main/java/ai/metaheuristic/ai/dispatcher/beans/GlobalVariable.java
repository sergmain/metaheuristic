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
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

@Entity
@Table(name = "MH_VARIABLE_GLOBAL")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@ToString(exclude={"data", "bytes"})
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
