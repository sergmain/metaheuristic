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

import ai.metaheuristic.ai.yaml.series.SeriesParamsYaml;
import ai.metaheuristic.ai.yaml.series.SeriesParamsYamlUtils;
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
 * Date: 3/30/2021
 * Time: 1:21 PM
 */
@Entity
@Table(name = "MH_SERIES")
@Data
@ToString
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Series  implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = -2232274612309877419L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "NAME")
    public String name;

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
    private final ThreadUtils.CommonThreadLocker<SeriesParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private SeriesParamsYaml parseParams() {
        SeriesParamsYaml temp = SeriesParamsYamlUtils.BASE_YAML_UTILS.to(params);
        SeriesParamsYaml ecpy = temp==null ? new SeriesParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public SeriesParamsYaml getSeriesParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(SeriesParamsYaml epy) {
        setParams(SeriesParamsYamlUtils.BASE_YAML_UTILS.toString(epy));
    }
}