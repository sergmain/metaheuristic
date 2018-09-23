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

import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.snippet.SnippetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:38
 */
@Entity
@Table(name = "AIAI_LP_EXPERIMENT")
@Data
@EqualsAndHashCode(exclude = {"hyperParams", "snippets"})
@ToString(exclude = {"hyperParams", "snippets"})
public class Experiment implements Serializable {
    private static final long serialVersionUID = -3509391644278818781L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "DATASET_ID")
    private Long datasetId;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "EPOCH")
    private String epoch;

    @Column(name = "EPOCH_VARIANT")
    private int epochVariant;

    @Column(name = "SEED")
    private int seed;

    @Column(name = "IS_ALL_SEQUENCE_PRODUCED")
    private boolean isAllSequenceProduced;

    @Column(name = "IS_FEATURE_PRODUCED")
    private boolean isFeatureProduced;

    @Column(name = "EXEC_STATE")
    private int execState;

    @Column(name = "IS_LAUNCHED")
    private boolean isLaunched;

    @Column(name="CREATED_ON")
    private long createdOn;

    @Column(name="LAUNCHED_ON")
    private Long launchedOn;

    @Column(name = "NUMBER_OF_SEQUENCE")
    private int numberOfSequence;

    // we need eager because of @Scheduled

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    private List<ExperimentHyperParams> hyperParams;

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    private List<ExperimentSnippet> snippets;

    public void sortSnippetsByOrder() {
        snippets.sort(Comparator.comparingInt(ExperimentSnippet::getOrder));
    }


    public boolean hasFit() {
        if (snippets==null || snippets.isEmpty()) {
            return false;
        }
        for (ExperimentSnippet snippet : snippets) {
            if (SnippetType.fit.toString().equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPredict() {
        if (snippets==null || snippets.isEmpty()) {
            return false;
        }
        for (ExperimentSnippet snippet : snippets) {
            if (SnippetType.predict.toString().equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Map<String, Integer>> getHyperParamsAsMap() {
        return getHyperParamsAsMap(true);
    }

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
