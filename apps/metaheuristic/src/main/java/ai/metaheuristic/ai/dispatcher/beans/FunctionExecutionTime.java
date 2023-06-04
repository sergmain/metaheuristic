/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

import ai.metaheuristic.ai.yaml.function_execution_time.FunctionExecutionTimeParamsYaml;
import ai.metaheuristic.ai.yaml.function_execution_time.FunctionExecutionTimeParamsYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 11/1/2022
 * Time: 10:17 PM
 */
@Entity
@Table(name = "MH_FUNCTION_EXECUTION_TIME")
@Data
@EqualsAndHashCode(of = {"keySha256Length"})
@NoArgsConstructor
public class FunctionExecutionTime implements Serializable {
    @Serial
    private static final long serialVersionUID = -9131005645862711982L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name="CREATED_ON")
    public long createdOn;

    @NotNull
    @NotEmpty
    @Column(name = "FUNCTION_TYPE")
    public String functionCode;

    /**
     * this field contains SHA256 checksum AND the length of data
     */
    @NotNull
    @NotEmpty
    @Column(name = "KEY_SHA256_LENGTH")
    public String keySha256Length;

    @NotNull
    @NotEmpty
    @Column(name = "KEY_VALUE")
    public String keyValue;

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
    private FunctionExecutionTimeParamsYaml ecpy = null;

    @JsonIgnore
    public FunctionExecutionTimeParamsYaml getFunctionExecutionTimeParamsYaml() {
        if (ecpy ==null) {
            synchronized (this) {
                if (ecpy ==null) {
                    FunctionExecutionTimeParamsYaml temp = FunctionExecutionTimeParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    ecpy = temp==null ? new FunctionExecutionTimeParamsYaml() : temp;
                }
            }
        }
        return ecpy;
    }

    @JsonIgnore
    public void updateParams(FunctionExecutionTimeParamsYaml wpy) {
        setParams(FunctionExecutionTimeParamsYamlUtils.BASE_YAML_UTILS.toString(wpy));
    }

}

