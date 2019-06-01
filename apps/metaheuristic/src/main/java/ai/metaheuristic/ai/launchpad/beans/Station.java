/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:56
 */
@Entity
@Table(name = "MH_STATION")
@Data
@ToString(exclude = "status")
public class Station implements Serializable {
    private static final long serialVersionUID = -6094247705164836600L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /**
     * When status of station was updated last time
     */
    @Column(name="UPDATED_ON")
    public long updatedOn;

    /**
     * this field is initialized manually
     */
    @Column(name = "IP")
    public String ip;

    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "STATUS")
    public String status;

}

