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

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;

/**
 * @author Serge
 * Date: 10/7/2020
 * Time: 9:13 PM
 */
/*
@Entity
@Table(name = "MH_CACHE")
@Data
@EqualsAndHashCode(of = {"keySha256Length"})
@ToString(exclude={"data", "bytes"})
@NoArgsConstructor
*/
public class Cache implements Serializable {
    private static final long serialVersionUID = -1541017259044013865L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name="CREATED_ON")
    public long createdOn;

    @NonNull
    @Column(name = "KEY_SHA256_LENGTH")
    public String keySha256Length;

    @NonNull
    @Column(name = "KEY_VALUE")
    public String keyValue;

    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Transient
    public byte[] bytes;

}
