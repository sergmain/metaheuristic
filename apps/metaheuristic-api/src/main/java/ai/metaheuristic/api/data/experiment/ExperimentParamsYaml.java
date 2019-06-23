/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.api.data.experiment;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 10:03 PM
 */
@Data
@NoArgsConstructor
public class ExperimentParamsYaml {

    public Long id;

    @Version
    private Integer version;

    @Column(name = "WORKBOOK_ID")
    public Long workbookId;

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

}
