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

package ai.metaheuristic.ai.launchpad.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

@Entity
@Table(name = "mh_variable")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@ToString(exclude={"data", "bytes"})
public class GlobalVariable implements Serializable {
    private static final long serialVersionUID = 5114121077582180465L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "NAME")
    public String variable;

    @Column(name = "UPLOAD_TS")
    public Timestamp uploadTs;

    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Column(name = "FILENAME")
    public String filename;

    @Transient
    public byte[] bytes;

    @Column(name = "PARAMS")
    public String params;

}
