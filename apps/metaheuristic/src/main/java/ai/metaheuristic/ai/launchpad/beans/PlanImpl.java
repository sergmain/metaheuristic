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

import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Table(name = "MH_PLAN")
@Data
public class PlanImpl implements Serializable, Plan {
    private static final long serialVersionUID = 6764501814772365639L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "CODE")
    public String code;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "PARAMS")
    public String params;

    @Column(name = "IS_LOCKED")
    public boolean locked;

    @Column(name = "IS_VALID")
    public boolean valid;

    @Transient
    @JsonIgnore
    private PlanParamsYaml ppy = null;

    // for controlling of SnakeYaml
    @SuppressWarnings("unused")
    @Transient
    private PlanParamsYaml getPpy(){
        return ppy;
    }

    @JsonIgnore
    public PlanParamsYaml getPlanParamsYaml() {
        if (ppy ==null) {
            synchronized (this) {
                if (ppy ==null) {
                    //noinspection UnnecessaryLocalVariable
                    PlanParamsYaml temp = PlanParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    ppy = temp;
                }
            }
        }
        return ppy;
    }

    @JsonIgnore
    public void updateParams(PlanParamsYaml ppy) {
        params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
    }
}