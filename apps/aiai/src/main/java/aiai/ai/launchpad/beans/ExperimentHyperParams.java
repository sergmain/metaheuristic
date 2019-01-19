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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_EXPERIMENT_HYPER_PARAMS")
@Data
@EqualsAndHashCode(exclude = {"experiment", "variants"})
@ToString(exclude = {"experiment"})
@NoArgsConstructor
public class ExperimentHyperParams implements Serializable {
    private static final long serialVersionUID = -2816493662535597212L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "HYPER_PARAM_KEY")
    private String key;

    @Column(name = "HYPER_PARAM_VALUES")
    private String values;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EXPERIMENT_ID")
    private Experiment experiment;

    public ExperimentHyperParams(String key, String values, Experiment experiment) {
        this.key = key;
        this.values = values;
        this.experiment = experiment;
    }

    /**
     * number of variants for this metadata
     */
    @Transient
    private int variants;


}