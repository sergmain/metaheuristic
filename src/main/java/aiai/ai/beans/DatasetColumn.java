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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 21:27
 */
@Entity
@Table(name = "AIAI_LP_DATASET_COLUMN")
@Data
@EqualsAndHashCode(exclude = {"datasetGroup"})
@ToString(exclude = {"datasetGroup"})
public class DatasetColumn implements Serializable {
    private static final long serialVersionUID = -1823497750685166069L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "NAME")
    private String name;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "DATASET_GROUP_ID")
    private DatasetGroup datasetGroup;

    /**
     * last column in definition. is used in UI
     */
    @Transient
    private boolean isLastColumn;

}
