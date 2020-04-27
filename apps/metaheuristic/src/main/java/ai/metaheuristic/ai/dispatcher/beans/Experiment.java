/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:38
 */
@Entity
@Table(name = "MH_EXPERIMENT")
@Data
@ToString(exclude = {"epy"})
@NoArgsConstructor
public class Experiment implements Serializable, Cloneable {
    private static final long serialVersionUID = -3509391644278818781L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    // 1-1 relation with ExecContext
    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @Column(name = "CODE")
    public String code;

    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.epy=null;
        }
    }

    @NonNull
    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private ExperimentParamsYaml epy = null;

    @SneakyThrows
    public Experiment clone() {
        return (Experiment) super.clone();
    }

    @JsonIgnore
    public @NonNull ExperimentParamsYaml getExperimentParamsYaml() {
        if (epy==null) {
            synchronized (this) {
                if (epy==null) {
                    ExperimentParamsYaml temp = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    epy = temp==null ? new ExperimentParamsYaml() : temp;
                }
            }
        }
        return epy;
    }

    @JsonIgnore
    public void updateParams(ExperimentParamsYaml epy) {
        params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.toString(epy);
    }


}
