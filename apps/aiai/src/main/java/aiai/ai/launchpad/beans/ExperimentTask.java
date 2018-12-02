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
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_EXPERIMENT_TASK")
@Data
@ToString
public class ExperimentTask implements Serializable {
    private static final long serialVersionUID = -5195420088301863165L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "FLOW_INSTANCE_ID")
    public Long flowInstanceId;

    @Column(name = "FIT_TASK_ID")
    public Long fitTaskId;

    @Column(name = "PREDICT_TASK_ID")
    public Long predictTaskId;

    @Column(name = "FIT_EXEC_STATE")
    public int fitExecState;

    @Column(name = "PREDICT_EXEC_STATE")
    public int predictExecState;

}