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

import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYamlUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;

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
@ToString
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

    @Column(name = "CODE")
    public String code;

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
    private final ThreadUtils.CommonThreadLocker<DispatcherParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private DispatcherParamsYaml parseParams() {
        DispatcherParamsYaml temp = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(params);
        DispatcherParamsYaml ecpy = temp==null ? new DispatcherParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public DispatcherParamsYaml getDispatcherParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(DispatcherParamsYaml tpy) {
        setParams(DispatcherParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }


}
