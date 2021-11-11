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

import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:22 AM
 */
@Entity
@Table(name = "MH_EXEC_CONTEXT_TASK_STATE")
@Data
@NoArgsConstructor
@ToString(exclude = {"ecpy"})
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExecContextTaskState implements Serializable {
    @Serial
    private static final long serialVersionUID = -8849182851275372257L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Nullable
    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @NotBlank
    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.ecpy =null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private ExecContextTaskStateParamsYaml ecpy = null;

    @JsonIgnore
    public ExecContextTaskStateParamsYaml getExecContextTaskStateParamsYaml() {
        if (ecpy ==null) {
            synchronized (this) {
                if (ecpy ==null) {
                    ExecContextTaskStateParamsYaml temp = ExecContextTaskStateParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    ecpy = temp==null ? new ExecContextTaskStateParamsYaml() : temp;
                }
            }
        }
        return ecpy;
    }

    @JsonIgnore
    public void updateParams(ExecContextTaskStateParamsYaml wpy) {
        setParams(ExecContextTaskStateParamsYamlUtils.BASE_YAML_UTILS.toString(wpy));
    }
}
