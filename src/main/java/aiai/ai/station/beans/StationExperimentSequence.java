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
package aiai.ai.station.beans;

import aiai.ai.core.JsonUtils;
import aiai.ai.launchpad.experiment.ExperimentService;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_S_EXPERIMENT_SEQUENCE")
@Data
public class StationExperimentSequence implements Serializable {
    private static final long serialVersionUID = 6399969247304215923L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "EXPERIMENT_SEQUENCE_ID")
    private long experimentSequenceId;

    @Column(name="CREATED_ON")
    private long createdOn;

    @Column(name="LAUNCHED_ON")
    private Long launchedOn;

    @Column(name="FINISHED_ON")
    private Long finishedOn;

    @Column(name="REPORTED_ON")
    private Long reportedOn;

    @Column(name="PARAMS")
    private String params;

    @Column(name="METRICS")
    private String metrics;

    @Column(name = "IS_REPORTED")
    private boolean isReported;

    @Column(name = "IS_DELIVERED")
    private boolean isDelivered;

    @Column(name="SNIPPET_EXEC_RESULTS")
    private String snippetExecResults;

}
