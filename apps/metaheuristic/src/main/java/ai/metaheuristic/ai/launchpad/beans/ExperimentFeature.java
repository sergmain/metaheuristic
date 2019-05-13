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

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "MH_EXPERIMENT_FEATURE")
@Data
public class ExperimentFeature implements Serializable {
    private static final long serialVersionUID = -7943373261306370650L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "RESOURCE_CODES")
    public String resourceCodes;

    @Column(name = "CHECKSUM_ID_CODES")
    public String checksumIdCodes;

    @Column(name = "EXEC_STATUS")
    public int execStatus;

    @Column(name = "EXPERIMENT_ID")
    public Long experimentId;

    @Column(name = "MAX_VALUE")
    public Double maxValue;

    public String execStatusAsString() {
        switch(execStatus) {
            case 0:
                return "Unknown";
            case 1:
                return "Ok";
            case 2:
                return "All are errors";
            case 3:
                return "No sequenses";
            default:
                return "Status is wrong";
        }
    }



}