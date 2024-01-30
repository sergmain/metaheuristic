/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
//import jakarta.validation.constraints.NotBlank;
//import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 5/3/2021
 * Time: 11:47 PM
 */
@Entity
@Table(name = "MH_HEURISTIC")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@ToString
@NoArgsConstructor
public class Heuristic implements Serializable {
    @Serial
    private static final long serialVersionUID = -2054668981438811654L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
//    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "IS_DELETED")
    public boolean deleted;

//    @NotBlank
    @Column(name = "PARAMS")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ExecContextParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecContextParamsYaml parseParams() {
        ExecContextParamsYaml temp = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(params);
        ExecContextParamsYaml ecpy = temp==null ? new ExecContextParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExecContextParamsYaml getExecContextParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExecContextParamsYaml tpy) {
        setParams(ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }
}
