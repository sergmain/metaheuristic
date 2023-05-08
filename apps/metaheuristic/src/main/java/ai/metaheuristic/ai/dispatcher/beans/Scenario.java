/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.yaml.scenario.ScenarioParamsUtils;
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

/*
CREATE table mhbp_scenario
(
    ID                  INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             INT UNSIGNED    NOT NULL,
    ACCOUNT_ID          INT UNSIGNED    NOT NULL,
    SCENARIO_GROUP_ID   INT UNSIGNED    NOT NULL,
    CREATED_ON          bigint          NOT NULL,
    NAME                VARCHAR(50)     NOT NULL,
    DESCRIPTION         VARCHAR(250)    NOT NULL
);
 */
    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "API_ID")
    public long apiId;

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