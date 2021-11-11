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

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 10/7/2020
 * Time: 9:13 PM
 */
@Entity
@Table(name = "MH_CACHE_PROCESS")
@Data
@EqualsAndHashCode(of = {"keySha256Length"})
@NoArgsConstructor
public class CacheProcess implements Serializable {
    @Serial
    private static final long serialVersionUID = -1541017259044013865L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name="CREATED_ON")
    public long createdOn;

    @NotNull
    @NotEmpty
    @Column(name = "FUNCTION_CODE")
    public String functionCode;

    /**
     * this field contains SHA256 checksum AND the length of data
     */
    @NotNull
    @NotEmpty
    @Column(name = "KEY_SHA256_LENGTH")
    public String keySha256Length;

    @NotNull
    @NotEmpty
    @Column(name = "KEY_VALUE")
    public String keyValue;

}
