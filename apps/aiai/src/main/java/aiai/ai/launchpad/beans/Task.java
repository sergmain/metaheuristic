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
package aiai.ai.launchpad.beans;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_TASK")
@Data
public class Task implements Serializable {
    private static final long serialVersionUID = 268796211406267810L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "PARAMS")
    public String params;

    @Column(name = "STATION_ID")
    public Long stationId;

    @Column(name = "ASSIGNED_ON")
    public Long assignedOn;

    @Column(name = "COMPLETED_ON")
    public Long completedOn;

    @Column(name = "IS_COMPLETED")
    public boolean isCompleted;

    @Column(name = "IS_ALL_SNIPPETS_OK")
    public boolean isAllSnippetsOk;

    @Column(name = "SNIPPET_EXEC_RESULTS")
    public String snippetExecResults;

    @Column(name = "METRICS")
    public String metrics;

    @Column(name = "TASK_TYPE")
    public String taskType;

    @Column(name = "TASK_ORDER")
    public String order;
}