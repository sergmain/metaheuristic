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

import ai.metaheuristic.ai.Enums;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "MH_LOG_DATA")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@NoArgsConstructor
public class LogData implements Serializable {
    @Serial
    private static final long serialVersionUID = -6065599957629315147L;

    public void setType(Enums.LogType type) {
        this.logType = type.typeNumber;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "REF_ID")
    public Long refId;

    @Column(name = "UPDATE_TS")
    public Timestamp updateTs;

    @Column(name = "LOG_DATA")
    public String logData;

    @Column(name = "LOG_TYPE")
    public int logType;

}
