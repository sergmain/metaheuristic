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

import javax.persistence.*;
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
public class Ids implements Serializable {
    private static final long serialVersionUID = 8697932300220763332L;

//    @GeneratedValue(strategy = GenerationType.IDENTITY)

    @Id
    @GeneratedValue(strategy= GenerationType.TABLE, generator = "mh_ids")
    public Long id;

    public Integer stub;
}