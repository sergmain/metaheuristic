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
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

/**
 * @author Serge
 * Date: 5/4/2021
 * Time: 11:40 PM
 */
@Entity
@Table(name = "MH_EVALUATION")
@Data
@NoArgsConstructor
public class Evaluation implements Serializable {
    @Serial
    private static final long serialVersionUID = 964853638400128485L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "HEURISTIC_ID")
    public Long heuristicId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "IS_DELETED")
    public boolean deleted;

    @Column(name = "PARAMS")
    public String params;

}

