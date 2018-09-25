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

import aiai.ai.Consts;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 19:54
 */
@Entity
@Table(name = "AIAI_LP_DATASET")
@Data
@EqualsAndHashCode(exclude = {"datasetGroups"})
@NoArgsConstructor
public class Dataset implements Serializable {
    private static final long serialVersionUID = -1972306380977162458L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CMD_ASSEMBLE")
    private String assemblingCommand;

    @Column(name = "CMD_PRODUCE")
    private String producingCommand;

    @Column(name = "IS_EDITABLE")
    private boolean isEditable;

    @Column(name = "IS_LOCKED")
    private boolean isLocked;

    @Column(name = "RAW_ASSEMBLING_STATUS")
    private int rawAssemblingStatus;

    @Column(name = "DATASET_PRODUCING_STATUS")
    private int datasetProducingStatus;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, fetch=FetchType.EAGER)
    private List<DatasetGroup> datasetGroups;

    public String asRawFilePath() {
        //noinspection UnnecessaryLocalVariable
        String rawFilePath = String.format("%s%c%06d%c%s", Consts.DATASET_DIR, File.separatorChar, id, File.separatorChar, Consts.RAW_FILE_NAME);
        return  rawFilePath;
    }

    public String asDatasetFilePath() {
        //noinspection UnnecessaryLocalVariable
        String datasetFilePath = String.format("%s%c%06d%cdataset%c%s", Consts.DATASET_DIR, File.separatorChar, id, File.separatorChar, File.separatorChar, Consts.DATASET_FILE_NAME);
        return  datasetFilePath;


    }


}
