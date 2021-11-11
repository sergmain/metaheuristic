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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
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
@ToString(exclude = {"spy"})
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

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.spy =null;
        }
    }

    @NonNull
    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private SeriesParamsYaml spy = null;

    @NonNull
    @JsonIgnore
    public SeriesParamsYaml getSeriesParamsYaml() {
        if (spy ==null) {
            synchronized (this) {
                if (spy ==null) {
                    SeriesParamsYaml temp = SeriesParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    spy = temp==null ? new SeriesParamsYaml() : temp;
                }
            }
        }
        return spy;
    }

    @JsonIgnore
    public void updateParams(SeriesParamsYaml epy) {
        setParams(SeriesParamsYamlUtils.BASE_YAML_UTILS.toString(epy));
    }


}