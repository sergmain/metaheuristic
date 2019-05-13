/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.api.v1.launchpad;

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 3:33 PM
 */
public interface Workbook {
    Long getId();

    Integer getVersion();

    Long getPlanId();

    long getCreatedOn();

    Long getCompletedOn();

    String getInputResourceParam();

    int getProducingOrder();

    boolean isValid();

    int getExecState();

    void setId(Long id);

    void setVersion(Integer version);

    void setPlanId(Long planId);

    void setCreatedOn(long createdOn);

    void setCompletedOn(Long completedOn);

    void setInputResourceParam(String inputResourceParam);

    void setProducingOrder(int producingOrder);

    void setValid(boolean valid);

    void setExecState(int execState);
}
