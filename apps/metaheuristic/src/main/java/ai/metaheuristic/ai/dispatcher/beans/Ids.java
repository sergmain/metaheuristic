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
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 8:05 PM
 */
@Entity
@Table(name = "MH_IDS")
@Data
@TableGenerator(
        name="mh_ids",
        table="mh_gen_ids",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "mh_ids",
        allocationSize = 1,
        initialValue = 1
)
@NoArgsConstructor
public class Ids implements Serializable {
    @Serial
    private static final long serialVersionUID = 8697932300220763332L;

//    @GeneratedValue(strategy = GenerationType.IDENTITY)

    @Id
    @GeneratedValue(strategy= GenerationType.TABLE, generator = "mh_ids")
    public Long id;

    public Integer stub;
}
