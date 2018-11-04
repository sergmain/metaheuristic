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
@Table(name = "AIAI_LP_EXPERIMENT_SNIPPET")
@Data
@ToString
public class ExperimentSnippet implements Serializable {
    private static final long serialVersionUID = -5262380060868481336L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "SNIPPET_CODE")
    private String snippetCode;

    @Column(name = "SNIPPET_TYPE")
    public String type;

    @Column(name = "SNIPPET_ORDER")
    private int order;

    @Column(name = "TASK_TYPE")
    private int taskType;

    @Column(name = "REF_ID")
    private long refId;
}