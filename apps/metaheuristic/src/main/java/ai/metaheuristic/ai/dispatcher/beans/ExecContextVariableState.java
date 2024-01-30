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

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtils;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
//import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

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
@ToString(exclude = {"paramsLocked"})
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

    @Nullable
    @Column(name="CREATED_ON")
    public Long createdOn;

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
    private final ThreadUtils.CommonThreadLocker<ExecContextApiData.ExecContextVariableStates> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecContextApiData.ExecContextVariableStates parseParams() {
        ExecContextApiData.ExecContextVariableStates temp = ExecContextUtils.getExecContextTasksStatesInfo(params);
        ExecContextApiData.ExecContextVariableStates ecpy = temp==null ? new ExecContextApiData.ExecContextVariableStates() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExecContextApiData.ExecContextVariableStates getExecContextVariableStateInfo() {
        return paramsLocked.get();
    }

    @JsonIgnore
    @SneakyThrows
    public void updateParams(ExecContextApiData.ExecContextVariableStates info) {
        setParams(JsonUtils.getMapper().writeValueAsString(info));
    }

}
