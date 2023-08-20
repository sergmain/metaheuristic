/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_FUNCTION")
@Data
@NoArgsConstructor
@Cacheable
@AllArgsConstructor
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Function implements Serializable {
    @Serial
    private static final long serialVersionUID = 7232827143557914909L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "FUNCTION_CODE")
    public String code;

    @Column(name = "FUNCTION_TYPE")
    public String type;

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
    private final ThreadUtils.CommonThreadLocker<FunctionConfigYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private FunctionConfigYaml parseParams() {
        FunctionConfigYaml temp = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(params);
        FunctionConfigYaml ecpy = temp==null ? new FunctionConfigYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public FunctionConfigYaml getFunctionConfigYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(FunctionConfigYaml tpy) {
        setParams(FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }

}
