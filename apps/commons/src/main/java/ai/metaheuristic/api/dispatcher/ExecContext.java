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

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 3:33 PM
 */
// We need an interface because of not putting an implementation (which is Entity bean) here
public interface ExecContext {
    Long getId();

    Integer getVersion();

    Long getSourceCodeId();

    long getCreatedOn();

    Long getCompletedOn();

    String getParams();

    boolean isValid();

    int getState();

    void setId(Long id);

    void setVersion(Integer version);

    void setSourceCodeId(Long sourceCodeId);

    void setCreatedOn(long createdOn);

    void setCompletedOn(Long completedOn);

    void setParams(String params);

    void setValid(boolean valid);

    void setState(int execState);
}
