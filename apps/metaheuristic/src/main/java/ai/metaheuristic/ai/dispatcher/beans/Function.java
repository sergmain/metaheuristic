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

import ai.metaheuristic.commons.yaml.function.FunctionRuntimeParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionRuntimeParamsYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_FUNCTION")
@Data
@NoArgsConstructor
@Cacheable
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
    public String params;

    @Column(name = "RT_PARAMS")
    private String runtimeParams;

    public void setRuntimeParams(String params) {
        synchronized (this) {
            this.runtimeParams = params;
            this.epy=null;
        }
    }

    @NonNull
    public String getRuntimeParams() {
        return runtimeParams;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private FunctionRuntimeParamsYaml epy = null;

    @JsonIgnore
    public @NonNull FunctionRuntimeParamsYaml getFunctionRuntimeParamsYaml() {
        if (epy==null) {
            synchronized (this) {
                if (epy==null) {
                    if (runtimeParams!=null) {
                        FunctionRuntimeParamsYaml temp = FunctionRuntimeParamsYamlUtils.BASE_YAML_UTILS.to(runtimeParams);
                        epy = temp == null ? new FunctionRuntimeParamsYaml() : temp;
                    }
                    else {
                        epy = new FunctionRuntimeParamsYaml();
                    }
                }
            }
        }
        return epy;
    }

    @JsonIgnore
    public void updateRuntimeParams(FunctionRuntimeParamsYaml epy) {
        setParams(FunctionRuntimeParamsYamlUtils.BASE_YAML_UTILS.toString(epy));
    }

}
