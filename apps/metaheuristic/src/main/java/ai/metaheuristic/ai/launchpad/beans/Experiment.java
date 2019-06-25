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

package ai.metaheuristic.ai.launchpad.beans;

import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:38
 */
@Entity
@Table(name = "MH_EXPERIMENT")
@Data
@ToString(exclude = {"params"})
public class Experiment implements Serializable {
    private static final long serialVersionUID = -3509391644278818781L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "WORKBOOK_ID")
    public Long workbookId;

    @Column(name = "CODE")
    public String code;

    @Column(name = "PARAMS")
    public String params;

    @Transient
    private ExperimentParamsYaml epy = null;

    @JsonIgnore
    public ExperimentParamsYaml getExperimentParamsYaml() {

        if (epy==null) {
            synchronized (this) {
                if (epy==null) {
                    //noinspection UnnecessaryLocalVariable
                    ExperimentParamsYaml temp = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    epy = temp;
                }
            }
        }
        return epy;
    }

    public void updateParams(ExperimentParamsYaml epy) {
        params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.toString(epy);
    }


}
