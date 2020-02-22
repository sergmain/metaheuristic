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

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 8/3/2019
 * Time: 1:15 AM
 */
@Entity
@Table(name = "MH_ATLAS_TASK")
@Data
public class AtlasTask implements Serializable {
    private static final long serialVersionUID = -1225513309547284431L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "ATLAS_ID")
    public Long atlasId;

    @Column(name = "TASK_ID")
    public Long taskId;

    @Column(name = "PARAMS")
    public String params;
}
