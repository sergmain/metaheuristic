/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */

package aiai.ai.beans;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 17:31
 */
@Entity
@Table(name = "AIAI_S_ENV")
@TableGenerator(
        name = "TABLE_AIAI_ENV",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_ENV",
        allocationSize = 1,
        initialValue = 1
)
@Data
public class Env implements Serializable {
    private static final long serialVersionUID = -1968238169998898753L;

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_ENV")
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Integer version;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

}
