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
 * Date: 15.06.2017
 * Time: 19:54
 */
@Entity
@Table(name = "AIAI_LP_DATASET")
@TableGenerator(
        name = "TABLE_AIAI_DATASET",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_DATASET",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(exclude = {"datasetGroups"})
public class Dataset implements Serializable {
    private static final long serialVersionUID = -1972306380977162458L;
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET")
    private Long id;
    @Version
    @Column(name = "VERSION")
    private Integer version;
    @Column(name = "DESCRIPTION")
    private String description;
    @Column(name = "IS_HEADER")
    private boolean isHeader;
    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL)
    private List<DatasetGroup> datasetGroups;


    public Dataset() {
    }
}
