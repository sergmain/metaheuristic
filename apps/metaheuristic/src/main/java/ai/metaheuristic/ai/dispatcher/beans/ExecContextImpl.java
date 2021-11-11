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

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_EXEC_CONTEXT")
@Data
@NoArgsConstructor
@ToString(exclude = {"ecpy"})
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExecContextImpl implements Serializable, ExecContext {
    @Serial
    private static final long serialVersionUID = -8687758209537096490L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "SOURCE_CODE_ID")
    public Long sourceCodeId;

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Nullable
    @Column(name="COMPLETED_ON")
    public Long completedOn;

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

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "STATE")
    public int state;

    @Column(name = "CTX_GRAPH_ID")
    public Long execContextGraphId;

    @Column(name = "CTX_TASK_STATE_ID")
    public Long execContextTaskStateId;

    @Column(name = "CTX_VARIABLE_STATE_ID")
    public Long execContextVariableStateId;

    @Nullable
    @Column(name = "ROOT_EXEC_CONTEXT_ID")
    public Long rootExecContextId;

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

    @JsonIgnore
    public ExecContextData.SimpleExecContext asSimple() {
        return new ExecContextData.SimpleExecContext(
                sourceCodeId, id, execContextGraphId, execContextTaskStateId,
                execContextVariableStateId, companyId, getExecContextParamsYaml());
    }
}