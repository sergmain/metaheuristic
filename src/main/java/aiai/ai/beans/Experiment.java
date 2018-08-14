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

import aiai.ai.launchpad.snippet.SnippetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

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

    @Column(name = "IS_STARTED")
    private boolean isStarted;

    @Column(name = "IS_LAUNCHED")
    private boolean isLaunched;

    @Column(name="CREATED_ON")
    private long createdOn;

    @Column(name="LAUNCHED_ON")
    private Long launchedOn;

    @Column(name = "NUMBER_OF_SEQUNCE")
    private int numberOfSequence;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ExperimentHyperParams> hyperParams;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    private List<ExperimentSnippet> snippets;

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
}
