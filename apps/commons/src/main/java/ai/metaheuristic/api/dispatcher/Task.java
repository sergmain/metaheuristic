/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.api.dispatcher;

import org.springframework.lang.Nullable;

// We need an interface because of not putting an implementation (which is Entity bean) here
public interface Task {
    Long getId();

    Integer getVersion();

    String getParams();

    @Nullable
    Long getCoreId();

    @Nullable
    Long getAssignedOn();

    @Nullable
    Long getCompletedOn();

    boolean isCompleted();

    @Nullable
    String getFunctionExecResults();

    Long getExecContextId();

    int getExecState();

    boolean isResultReceived();

    long getResultResourceScheduledOn();

    void setId(Long id);

    void setVersion(Integer version);

    void setParams(String params);

    void setCoreId(@Nullable Long processorId);

    void setAssignedOn(@Nullable Long assignedOn);

    void setCompletedOn(@Nullable Long completedOn);

    void setCompleted(boolean isCompleted);

    void setFunctionExecResults(@Nullable String functionExecResults);

    void setExecContextId(Long execContextId);

    void setExecState(int execState);

    void setResultReceived(boolean resultReceived);

    void setResultResourceScheduledOn(long resultResourceScheduledOn);

    @Nullable
    Long getUpdatedOn();

    void setUpdatedOn(@Nullable Long updatedOn);

    Long getAccessByProcessorOn();

    void setAccessByProcessorOn(Long accessByProcessorOn);
}
