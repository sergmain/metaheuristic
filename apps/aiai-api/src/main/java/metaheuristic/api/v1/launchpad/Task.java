/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package metaheuristic.api.v1.launchpad;

public interface Task {
    Long getId();

    Integer getVersion();

    String getParams();

    Long getStationId();

    Long getAssignedOn();

    Long getCompletedOn();

    boolean isCompleted();

    String getSnippetExecResults();

    String getMetrics();

    int getOrder();

    long getWorkbookId();

    int getExecState();

    int getProcessType();

    boolean isResultReceived();

    long getResultResourceScheduledOn();

    void setId(Long id);

    void setVersion(Integer version);

    void setParams(String params);

    void setStationId(Long stationId);

    void setAssignedOn(Long assignedOn);

    void setCompletedOn(Long completedOn);

    void setCompleted(boolean isCompleted);

    void setSnippetExecResults(String snippetExecResults);

    void setMetrics(String metrics);

    void setOrder(int order);

    void setWorkbookId(long workbookId);

    void setExecState(int execState);

    void setProcessType(int processType);

    void setResultReceived(boolean resultReceived);

    void setResultResourceScheduledOn(long resultResourceScheduledOn);
}
