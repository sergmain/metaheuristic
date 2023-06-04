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

import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_TASK")
@Data
@ToString(exclude = {"params"} )
@NoArgsConstructor
@EntityListeners(value=TaskImpl.LastUpdateListener.class)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class TaskImpl implements Serializable, Task {
    @Serial
    private static final long serialVersionUID = 5618845653823904624L;

    public static class LastUpdateListener {

        @SuppressWarnings("MethodMayBeStatic")
        @PrePersist
        public void setLastUpdate(TaskImpl o) {
            o.setUpdatedOn( System.currentTimeMillis() );
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Nullable
    @Column(name = "CORE_ID")
    public Long coreId;

    @Nullable
    @Column(name = "ASSIGNED_ON")
    public Long assignedOn;

    @Nullable
    @Column(name = "UPDATED_ON")
    public Long updatedOn;

    @Nullable
    @Column(name = "COMPLETED_ON")
    public Long completedOn;

    @Column(name = "IS_COMPLETED")
    public int completed;

    @JsonIgnore
    @Nullable
    @Column(name = "FUNCTION_EXEC_RESULTS")
    public String functionExecResults;

    @NonNull
    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @Column(name = "EXEC_STATE")
    public int execState;

    // by result it means all outputs which are created by this task
    @Column(name = "IS_RESULT_RECEIVED")
    public int resultReceived;

    // resource==variable
    @Column(name = "RESULT_RESOURCE_SCHEDULED_ON")
    public long resultResourceScheduledOn;

    @Column(name = "ACCESS_BY_PROCESSOR_ON")
    public Long accessByProcessorOn;

    /**
     * TaskParamsYaml represented as a String
     */
    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.tpy =null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private TaskParamsYaml tpy = null;

    @JsonIgnore
    public TaskParamsYaml getTaskParamsYaml() {
        if (tpy==null) {
            synchronized (this) {
                if (tpy==null) {
                    //noinspection UnnecessaryLocalVariable
                    TaskParamsYaml temp = TaskParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    tpy = temp;
                }
            }
        }
        return tpy;
    }

    @JsonIgnore
    public void updateParams(TaskParamsYaml tpy) {
        setParams(TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }

}