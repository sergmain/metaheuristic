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

package ai.metaheuristic.ai.mhbp.beans;

import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiScheme;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiSchemeUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils.CommonThreadLocker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 9:05 PM
 */
@Entity
@Table(name = "MHBP_API")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Api implements Serializable {
    @Serial
    private static final long serialVersionUID = -5515608565018985069L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "COMPANY_ID")
    public long companyId;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "NAME")
    public String name;

    @Column(name = "CODE")
    public String code;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name="DISABLED")
    public boolean disabled;

    @Column(name = "SCHEME")
    private String scheme;

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.paramsLocked.reset(()->this.scheme = scheme);
    }

    @Transient
    @JsonIgnore
    private final CommonThreadLocker<ApiScheme> paramsLocked = new CommonThreadLocker<>(this::parseScheme);

    private ApiScheme parseScheme() {
        ApiScheme temp = ApiSchemeUtils.UTILS.to(scheme);
        ApiScheme ecpy = temp==null ? new ApiScheme() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ApiScheme getApiScheme() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateScheme(ApiScheme apiScheme) {
        setScheme(ApiSchemeUtils.UTILS.toString(apiScheme));
    }

}
