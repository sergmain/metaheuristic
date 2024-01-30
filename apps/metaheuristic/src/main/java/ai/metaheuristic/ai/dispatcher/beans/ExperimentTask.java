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

import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultTaskParamsYamlUtils;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParams;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 8/3/2019
 * Time: 1:15 AM
 */
@Entity
@Table(name = "mh_experiment_task")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExperimentTask implements Serializable {
    @Serial
    private static final long serialVersionUID = -1225513309547284431L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "EXPERIMENT_RESULT_ID")
    public Long experimentResultId;

    @Column(name = "TASK_ID")
    public Long taskId;

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
    private final ThreadUtils.CommonThreadLocker<ExperimentResultTaskParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExperimentResultTaskParams parseParams() {
        ExperimentResultTaskParams temp = ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.to(params);
        ExperimentResultTaskParams ecpy = temp==null ? new ExperimentResultTaskParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ExperimentResultTaskParams getExperimentResultTaskParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExperimentResultTaskParams tpy) {
        setParams(ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }

}
