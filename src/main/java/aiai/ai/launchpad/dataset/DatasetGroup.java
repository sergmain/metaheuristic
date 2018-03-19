/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.launchpad.dataset;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 21:22
 */
@Entity
@Table(name = "AIAI_LP_DATASET_GROUP")
@TableGenerator(
        name = "TABLE_AIAI_DATASET_GROUP",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_DATASET_GROUP",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(exclude = {"dataset", "datasetColumns"})
public class DatasetGroup implements Serializable {
    private static final long serialVersionUID = -3161178396332333392L;
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET_GROUP")
    private Long id;
    @Version
    @Column(name = "VERSION")
    private Integer version;
    @Column(name = "GROUP_NUMBER")
    private int groupNumber;
    @Column(name = "DESCRIPTION")
    private String description;
    @Column(name = "CMD")
    private String command;

    @Column(name = "FEATURE_FILE")
    private String featureFile;

    @Column(name = "IS_ID_GROUP")
    private boolean isIdGroup;

    @Column(name = "IS_FEATURE")
    private boolean isFeature;

    @Column(name = "IS_LABEL")
    private boolean isLabel;
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "DATASET_ID")
    private Dataset dataset;
    @OneToMany(mappedBy = "datasetGroup", cascade = CascadeType.ALL)
    private List<DatasetColumn> datasetColumns;

    /**
     * used only for UI
     */
    @Transient
    private boolean isAddColumn;

    public DatasetGroup() {
    }

    public DatasetGroup(int groupNumber) {
        this.groupNumber = groupNumber;
    }

}
