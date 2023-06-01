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

import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParamsUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 2:06 PM
 */
@Entity
@Table(name = "MHBP_KB")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Kb implements Serializable {
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

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "CODE")
    public String code;

    @Column(name="DISABLED")
    public boolean disabled;

    @Column(name = "PARAMS")
    private String params;

    public int status;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.kbParams = null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private KbParams kbParams = null;

    @Transient
    @JsonIgnore
    private final Object syncParamsObj = new Object();

    @JsonIgnore
    public KbParams getKbParams() {
        if (kbParams==null) {
            synchronized (syncParamsObj) {
                if (kbParams==null) {
                    //noinspection UnnecessaryLocalVariable
                    KbParams temp = KbParamsUtils.UTILS.to(params);
                    kbParams = temp;
                }
            }
        }
        return kbParams;
    }

    @JsonIgnore
    public void updateParams(KbParams apy) {
        setParams(KbParamsUtils.UTILS.toString(apy));
    }

}
