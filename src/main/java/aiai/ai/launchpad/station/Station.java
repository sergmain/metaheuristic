/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.station;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:56
 */
@Entity
@Table(name = "AIAI_LP_STATION")
@TableGenerator(
        name = "TABLE_AIAI_STATION",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_STATION",
        allocationSize = 1,
        initialValue = 1
)
@Data
public class Station implements Serializable {
    private static final long serialVersionUID = -6094247705164836600L;
    @Version
    @Column(name = "VERSION")
    public Integer version;
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_STATION")
    private Long id;
    @Column(name = "IP")
    private String ip;
    @Column(name = "DESCRIPTION")
    private String description;

    public Station() {
    }


}

