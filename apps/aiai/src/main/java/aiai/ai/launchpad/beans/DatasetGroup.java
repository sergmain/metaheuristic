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
import aiai.ai.utils.SimpleSelectOption;
import aiai.apps.commons.utils.DirUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.*;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 21:22
 */
@Entity
@Table(name = "AIAI_LP_DATASET_GROUP")
@Data
@EqualsAndHashCode(exclude = {"dataset", "snippet"})
@ToString(exclude = {"dataset", "snippet"})
@NoArgsConstructor
public class DatasetGroup implements Serializable {
    private static final long serialVersionUID = -3161178396332333392L;

    public enum FEATURE_STATUS {
        NONE(0), OK(1), ERROR(2), OBSOLETE(3);

        public final int value;

        FEATURE_STATUS(int value) {
            this.value = value;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "GROUP_NUMBER")
    private int groupNumber;

    @Column(name = "DESCRIPTION")
    private String description;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "SNIPPET_ID")
    @NotFound(action = NotFoundAction.IGNORE)
    private SnippetBase snippet;

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

    @Column(name = "IS_REQUIRED")
    private boolean isRequired;

    @Column(name = "STATUS")
    private int featureStatus;

    @Column(name = "FEATURE_LENGTH")
    private Long length;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DATASET_ID")
    private Dataset dataset;

    /**
     * used only for UI
     */
    @Transient
    private boolean isAddColumn;

    @Transient
    public List<SimpleSelectOption> featureOptions;

    public DatasetGroup(int groupNumber) {
        this.groupNumber = groupNumber;
    }

    public String asFeatureFilePath() {
        final String featurePath = String.format("%s%c%06d%cfeature%c%06d%c"+Consts.FEATURE_FILE_MASK,
                Consts.DATASET_DIR, File.separatorChar, dataset.getId(), File.separatorChar, File.separatorChar, id, File.separatorChar, id);
        return featurePath;
    }

}
