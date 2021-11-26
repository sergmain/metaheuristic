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

import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYamlUtils;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 4:23 PM
 */
@Entity
@Table(name = "MH_DISPATCHER")
@Data
@NoArgsConstructor
@ToString(exclude = {"dpy"})
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Dispatcher implements Serializable {
    @Serial
    private static final long serialVersionUID = 2499919383081808903L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.dpy=null;
        }
    }

    public String getParams() {
        return params;
    }

    @Column(name = "CODE")
    public String code;

    @Transient
    @JsonIgnore
    @Nullable
    private DispatcherParamsYaml dpy = null;

    @JsonIgnore
    public DispatcherParamsYaml getDispatcherParamsYaml() {
        if (dpy ==null) {
            synchronized (this) {
                if (dpy ==null) {
                    // to create a valid structure of params
                    String p = S.b(params) ? DispatcherParamsYamlUtils.BASE_YAML_UTILS.toString(new DispatcherParamsYaml()) : params;
                    //noinspection UnnecessaryLocalVariable
                    DispatcherParamsYaml temp = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(p);
                    dpy = temp;
                }
            }
        }
        return dpy;
    }

    @JsonIgnore
    public void updateParams(DispatcherParamsYaml dpy) {
        setParams(DispatcherParamsYamlUtils.BASE_YAML_UTILS.toString(dpy));
    }
}
