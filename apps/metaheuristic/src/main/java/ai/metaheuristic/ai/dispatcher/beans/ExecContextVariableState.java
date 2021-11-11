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

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtils;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 3/20/2021
 * Time: 12:06 AM
 */
@Entity
@Table(name = "MH_EXEC_CONTEXT_VARIABLE_STATE")
@Data
@NoArgsConstructor
@ToString(exclude = {"ecpy"})
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExecContextVariableState implements Serializable {
    @Serial
    private static final long serialVersionUID = 6268316966739446701L;

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
    private ExecContextApiData.ExecContextVariableStates ecpy = null;

    @JsonIgnore
    public ExecContextApiData.ExecContextVariableStates getExecContextVariableStateInfo() {
        if (ecpy ==null) {
            synchronized (this) {
                if (ecpy ==null) {
                    ExecContextApiData.ExecContextVariableStates temp = ExecContextUtils.getExecContextTasksStatesInfo(params);
                    ecpy = temp==null ? new ExecContextApiData.ExecContextVariableStates() : temp;
                }
            }
        }
        return ecpy;
    }

    @JsonIgnore
    @SneakyThrows
    public void updateParams(ExecContextApiData.ExecContextVariableStates info) {
        setParams(JsonUtils.getMapper().writeValueAsString(info));
    }

}
