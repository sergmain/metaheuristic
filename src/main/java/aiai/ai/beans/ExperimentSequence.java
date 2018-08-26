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

import aiai.ai.core.JsonUtils;
import aiai.ai.launchpad.experiment.ExperimentService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_EXPERIMENT_SEQUENCE")
@Data
public class ExperimentSequence implements Serializable {
    private static final long serialVersionUID = -7027988813072979346L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "EXPERIMENT_ID")
    private Long experimentId;

    @Column(name = "FEATURE_ID")
    private Long featureId;

    @Column(name = "PARAMS")
    private String params;

    @Column(name = "STATION_ID")
    private Long stationId;

    @Column(name="ASSIGNED_ON")
    private Long assignedOn;

    @Column(name="IS_COMPLETED")
    private boolean isCompleted;

    @Column(name="SNIPPET_EXEC_RESULTS")
    private String snippetExecResults;

}