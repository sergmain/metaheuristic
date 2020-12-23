/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 10/27/2019
 * Time: 7:10 PM
 */
@Entity
@Table(name = "MH_COMPANY")
@Data
@NoArgsConstructor
@ToString(exclude = {"cpy"})
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Company implements Serializable {
    private static final long serialVersionUID = -159889135750827404L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "UNIQUE_ID")
    public Long uniqueId;

    @Column(name = "PARAMS")
    @Nullable
    private String params;

    public String name;

    public Company(String companyName) {
        this.name = companyName;
    }

    public void setParams(@Nullable String params) {
        synchronized (this) {
            this.params = params;
            this.cpy=null;
        }
    }

    @Nullable
    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private CompanyParamsYaml cpy = null;

    @JsonIgnore
    public CompanyParamsYaml getCompanyParamsYaml() {
        if (cpy==null) {
            synchronized (this) {
                if (cpy ==null) {
                    // to create a corrected structure of params
                    String p = S.b(params) ? CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(new CompanyParamsYaml()) : params;
                    //noinspection UnnecessaryLocalVariable
                    CompanyParamsYaml temp = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(p);
                    cpy = temp;
                }
            }
        }
        return cpy;
    }
}
