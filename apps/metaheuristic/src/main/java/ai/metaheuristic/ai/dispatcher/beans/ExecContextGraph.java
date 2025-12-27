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

import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYamlUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
//import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:22 AM
 */
@Entity
@Table(name = "MH_EXEC_CONTEXT_GRAPH")
@Data
@NoArgsConstructor
@ToString
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExecContextGraph implements Serializable {
    @Serial
    private static final long serialVersionUID = 1790345825460507592L;

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
    private final ThreadUtils.CommonThreadLocker<ExecContextGraphParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecContextGraphParamsYaml parseParams() {
        ExecContextGraphParamsYaml temp = ExecContextGraphParamsYamlUtils.BASE_YAML_UTILS.to(params);
        ExecContextGraphParamsYaml ecpy = temp==null ? new ExecContextGraphParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExecContextGraphParamsYaml getExecContextGraphParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExecContextGraphParamsYaml wpy) {
        setParams(ExecContextGraphParamsYamlUtils.BASE_YAML_UTILS.toString(wpy));
    }

}
