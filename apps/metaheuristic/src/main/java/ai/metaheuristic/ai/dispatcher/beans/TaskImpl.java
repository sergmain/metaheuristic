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
package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_TASK")
@Data
@ToString(exclude = {"paramsLocked", "params"} )
@NoArgsConstructor
@EntityListeners(value=TaskImpl.LastUpdateListener.class)
@Cacheable
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @EqualsAndHashCode.Include
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

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<TaskParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private TaskParamsYaml parseParams() {
        TaskParamsYaml temp = TaskParamsYamlUtils.BASE_YAML_UTILS.to(params);
        TaskParamsYaml ecpy = temp==null ? new TaskParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public TaskParamsYaml getTaskParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(TaskParamsYaml tpy) {
        setParams(TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }

}