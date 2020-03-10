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

import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "mh_exec_context")
@Data
@NoArgsConstructor
public class ExecContextImpl implements Serializable, ExecContext {
    private static final long serialVersionUID = -8687758209537096490L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonNull
    public Long id;

    @Version
    private Integer version;

    @Column(name = "SOURCE_CODE_ID")
    public Long sourceCodeId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name="COMPLETED_ON")
    public Long completedOn;

    @Column(name = "PARAMS")
    @NonNull
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.wpy=null;
        }
    }

    @NonNull
    public String getParams() {
        return params;
    }

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "STATE")
    public int state;

    @Transient
    @JsonIgnore
    @Nullable
    private ExecContextParamsYaml wpy = null;

    @JsonIgnore
    public @NonNull ExecContextParamsYaml getExecContextParamsYaml() {
        if (wpy ==null) {
            synchronized (this) {
                if (wpy ==null) {
                    ExecContextParamsYaml temp = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    wpy = temp==null ? new ExecContextParamsYaml() : temp;
                }
            }
        }
        return wpy;
    }

    @JsonIgnore
    public void updateParams(@NonNull ExecContextParamsYaml wpy) {
        params = ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(wpy);
    }
}