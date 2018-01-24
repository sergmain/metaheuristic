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

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 21:27
 */
@Entity
@Table(name = "AIAI_LP_DATASET_COLUMN")
@TableGenerator(
        name = "TABLE_AIAI_DATASET_COLUMN",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_DATASET_COLUMN",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(exclude = {"datasetGroup"})
public class DatasetColumn implements Serializable {
    private static final long serialVersionUID = -1823497750685166069L;
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET_COLUMN")
    private Long id;
    @Version
    @Column(name = "VERSION")
    private Integer version;
    @Column(name = "DESCRIPTION")
    private String description;
    @Column(name = "NAME")
    private String name;
    //    @ManyToOne(cascade = CascadeType.PERSIST)
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "DATASET_GROUP_ID")
    private DatasetGroup datasetGroup;
    /**
     * last column in definition. is used in UI
     */
    @Transient
    private boolean isLastColumn;


    public DatasetColumn() {
    }
}
