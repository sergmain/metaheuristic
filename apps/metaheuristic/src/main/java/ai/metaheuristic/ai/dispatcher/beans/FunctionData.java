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

package ai.metaheuristic.ai.mh.dispatcher..beans;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

@Entity
@Table(name = "mh_function_data")
@Data
@EqualsAndHashCode(of = {"functionCode"})
@ToString(exclude={"data", "bytes"})
public class FunctionData implements Serializable {
    private static final long serialVersionUID = 7768428475142175426L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "FUNCTION_CODE")
    private String functionCode;

    @Column(name = "UPLOAD_TS")
    private Timestamp uploadTs;

    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Transient
    public byte[] bytes;

    @Column(name = "PARAMS")
    public String params;

}
