/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.beans;

import aiai.ai.launchpad.experiment.ExperimentUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.*;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:38
 */
@Entity
@Table(name = "AIAI_LP_EXPERIMENT")
@Data
@EqualsAndHashCode(exclude = {"hyperParams"})
@ToString(exclude = {"hyperParams"})
public class Experiment implements Serializable {
    private static final long serialVersionUID = -3509391644278818781L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "FLOW_INSTANCE_ID")
    public Long flowInstanceId;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CODE")
    private String code;

    @Column(name = "SEED")
    private int seed;

    @Column(name = "IS_ALL_TASK_PRODUCED")
    private boolean isAllTaskProduced;

    @Column(name = "IS_FEATURE_PRODUCED")
    private boolean isFeatureProduced;

    @Column(name="CREATED_ON")
    private long createdOn;

    @Column(name = "NUMBER_OF_TASK")
    private int numberOfTask;

    // we need eager because of @Scheduled and cache

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    public  List<ExperimentHyperParams> hyperParams;

    public void strip() {
        name = StringUtils.strip(name);
        description = StringUtils.strip(description);
        code = StringUtils.strip(code);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Map<String, Map<String, Integer>> getHyperParamsAsMap() {
        return getHyperParamsAsMap(true);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Map<String, Map<String, Integer>> getHyperParamsAsMap(boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentHyperParams hyperParam : getHyperParams()) {
            ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues() );
            Map<String, Integer> map = new LinkedHashMap<>();
            paramByIndex.put(hyperParam.getKey(), map);
            for (int i = 0; i <ofVariants.values.size(); i++) {
                String value = ofVariants.values.get(i);


                map.put(isFull ? hyperParam.getKey()+'-'+value : value , i);
            }
        }
        return paramByIndex;
    }

}
