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
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_EXPERIMENT_FEATURE")
@Data
public class ExperimentFeature implements Serializable {
    private static final long serialVersionUID = -7943373261306370650L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "FEATURE_IDS")
    public String featureIds;

    @Column(name = "IS_IN_PROGRESS")
    public boolean isInProgress;

    @Column(name = "IS_FINISHED")
    public boolean isFinished;

    @Column(name = "EXEC_STATUS")
    public int execStatus;

    @Column(name = "EXPERIMENT_ID")
    public Long experimentId;

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