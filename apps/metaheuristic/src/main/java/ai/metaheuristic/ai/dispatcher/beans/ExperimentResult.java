/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsJsonUtils;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.*;
//import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_EXPERIMENT_RESULT")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExperimentResult implements Serializable {
    @Serial
    private static final long serialVersionUID = -1225513309547283331L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

//    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "NAME")
    public String name;

    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "CODE")
    public String code;

    @Column(name="CREATED_ON")
    public long createdOn;

    // even thought db field is 'experiment',  field of bean will be named as params
    @Column(name = "EXPERIMENT")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ExperimentResultParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExperimentResultParams parseParams() {
        ExperimentResultParams temp = ExperimentResultParamsJsonUtils.BASE_UTILS.to(params);
        ExperimentResultParams ecpy = temp==null ? new ExperimentResultParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExperimentResultParams getExperimentResultParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExperimentResultParams tpy) {
        setParams(TaskParamsYamlUtils.UTILS.toString(tpy));
    }

}