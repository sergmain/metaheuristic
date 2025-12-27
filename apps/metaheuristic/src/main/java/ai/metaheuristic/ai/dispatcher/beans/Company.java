/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
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
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Company implements Serializable {
    @Serial
    private static final long serialVersionUID = -159889135750827404L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "UNIQUE_ID")
    public Long uniqueId;

    public String name;

    public Company(String companyName) {
        this.name = companyName;
    }

    @Column(name = "PARAMS")
    private String params;

    @Nullable
    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<CompanyParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private CompanyParamsYaml parseParams() {
        if (params==null) {
            return new CompanyParamsYaml();
        }
        CompanyParamsYaml temp = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(params);
        CompanyParamsYaml ecpy = temp==null ? new CompanyParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public CompanyParamsYaml getCompanyParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(CompanyParamsYaml tpy) {
        setParams(CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }

}
