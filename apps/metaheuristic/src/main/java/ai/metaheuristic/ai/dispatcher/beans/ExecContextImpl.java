/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.utils.threads.ThreadUtils.CommonThreadLocker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
//import jakarta.validation.constraints.NotBlank;
//import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_EXEC_CONTEXT")
@Data
@NoArgsConstructor
@ToString(exclude = {"paramsLocked", "params"})
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
//    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Nullable
    @Column(name = "ACCOUNT_ID")
    public Long accountId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Nullable
    @Column(name="COMPLETED_ON")
    public Long completedOn;

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "STATE")
    public int state;

    @Column(name = "CTX_GRAPH_ID")
    public Long execContextGraphId;

    @Nullable
    @Column(name = "CTX_TASK_STATE_ID")
    public Long execContextTaskStateId;

    @Column(name = "CTX_VARIABLE_STATE_ID")
    public Long execContextVariableStateId;

    @Nullable
    @Column(name = "ROOT_EXEC_CONTEXT_ID")
    public Long rootExecContextId;

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
    private final CommonThreadLocker<ExecContextParamsYaml> paramsLocked = new CommonThreadLocker<>(this::parseParams);

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
    public void updateParams(ExecContextParamsYaml wpy) {
        setParams(ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(wpy));
    }

    @JsonIgnore
    public ExecContextApiData.SimpleExecContext asSimple() {
        return new ExecContextApiData.SimpleExecContext(
                sourceCodeId, id, execContextGraphId, execContextTaskStateId,
                execContextVariableStateId, companyId, accountId, getExecContextParamsYaml());
    }
}