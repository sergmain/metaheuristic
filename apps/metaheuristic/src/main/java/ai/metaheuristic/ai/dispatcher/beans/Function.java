/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "MH_FUNCTION")
@Data
@NoArgsConstructor
@ToString(exclude = "fcy")
public class Function implements Serializable {
    private static final long serialVersionUID = 4066977399166436522L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Nullable
    public Long id;

    @Version
    @Nullable
    private Integer version;

    @Column(name = "FUNCTION_CODE")
    public String code;

    @Column(name = "FUNCTION_TYPE")
    public String type;

    @Column(name = "PARAMS")
    public String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.fcy =null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    private @Nullable FunctionConfigYaml fcy = null;

    @JsonIgnore
    public FunctionConfigYaml getFunctionConfig(boolean isClone) {
        if (fcy ==null) {
            synchronized (this) {
                if (fcy ==null) {
                    //noinspection UnnecessaryLocalVariable
                    FunctionConfigYaml temp = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(params);
                    fcy = temp;
                    return fcy;
                }
            }
        }
        return isClone ? fcy.clone() : fcy;
    }

    public void reset() {
        synchronized (this) {
            fcy = null;
        }
    }

}
