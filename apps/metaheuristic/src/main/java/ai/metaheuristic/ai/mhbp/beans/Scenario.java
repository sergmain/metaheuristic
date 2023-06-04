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

import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParamsUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 6:00 PM
 */
@Entity
@Table(name = "MHBP_SCENARIO")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Scenario implements Serializable {
    @Serial
    private static final long serialVersionUID = 3953889239221294113L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "SCENARIO_GROUP_ID")
    public long scenarioGroupId;

    @Column(name = "CREATED_ON")
    public long createdOn;

    @Column(name = "NAME")
    public String name;

    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.partParams = null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private ScenarioParams partParams = null;

    @Transient
    @JsonIgnore
    private final Object syncParamsObj = new Object();

    @JsonIgnore
    public ScenarioParams getScenarioParams() {
        if (partParams==null) {
            synchronized (syncParamsObj) {
                if (partParams==null) {
                    //noinspection UnnecessaryLocalVariable
                    ScenarioParams temp = ScenarioParamsUtils.UTILS.to(params);
                    partParams = temp;
                }
            }
        }
        return partParams;
    }

    @JsonIgnore
    public void updateParams(ScenarioParams apy) {
        setParams(ScenarioParamsUtils.UTILS.toString(apy));
    }

}