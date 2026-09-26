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

import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParams;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

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
@ToString
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
    private final ThreadUtils.CommonThreadLocker<ExecContextTaskStateParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecContextTaskStateParams parseParams() {
        ExecContextTaskStateParams temp = ExecContextTaskStateParamsUtils.BASE_UTILS.to(params);
        ExecContextTaskStateParams ecpy = temp==null ? new ExecContextTaskStateParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExecContextTaskStateParams getExecContextTaskStateParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExecContextTaskStateParams wpy) {
        setParams(ExecContextTaskStateParamsUtils.BASE_UTILS.toString(wpy));
    }

}
