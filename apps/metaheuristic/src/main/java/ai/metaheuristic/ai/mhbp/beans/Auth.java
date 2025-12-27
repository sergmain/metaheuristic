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

package ai.metaheuristic.ai.mhbp.beans;

import ai.metaheuristic.commons.yaml.auth.ApiAuth;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 4/12/2023
 * Time: 11:54 PM
 */
@Entity
@Table(name = "MHBP_AUTH")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Auth implements Serializable {
    @Serial
    private static final long serialVersionUID = 8980507647964174889L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "COMPANY_ID")
    public long companyId;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "CODE")
    public String code;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name="DISABLED")
    public boolean disabled;

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
    private final ThreadUtils.CommonThreadLocker<ApiAuth> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ApiAuth parseParams() {
        ApiAuth temp = ApiAuthUtils.UTILS.to(params);
        ApiAuth ecpy = temp==null ? new ApiAuth() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ApiAuth getAuthParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ApiAuth scspy) {
        setParams(ApiAuthUtils.UTILS.toString(scspy));
    }
}
