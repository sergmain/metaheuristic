/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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
@ToString(exclude={"ecpy"})
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
    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @NotBlank
    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.ecpy = null;
        }
    }

    @Column(name = "IS_DELETED")
    public boolean deleted;

    @Transient
    @JsonIgnore
    @Nullable
    private ExecContextParamsYaml ecpy = null;

    @JsonIgnore
    public ExecContextParamsYaml getExecContextParamsYaml() {
        if (ecpy ==null) {
            synchronized (this) {
                if (ecpy ==null) {
                    ExecContextParamsYaml temp = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    ecpy = temp==null ? new ExecContextParamsYaml() : temp;
                }
            }
        }
        return ecpy;
    }

    @JsonIgnore
    public void updateParams(ExecContextParamsYaml wpy) {
        setParams(ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(wpy));
    }
}
