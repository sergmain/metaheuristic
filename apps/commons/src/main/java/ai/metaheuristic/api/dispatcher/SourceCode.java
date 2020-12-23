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
 * Time: 3:27 PM
 */
// We need an interface because of not putting an implementation (which is Entity bean) here
public interface SourceCode {
    Long getId();

    Long getCompanyId();

    Integer getVersion();

    String getUid();

    long getCreatedOn();

    String getParams();

    boolean isLocked();

    boolean isValid();

    void setId(Long id);

    void setCompanyId(Long companyId);

    void setVersion(Integer version);

    void setUid(String uid);

    void setCreatedOn(long createdOn);

    void setParams(String params);

    void setLocked(boolean locked);

    void setValid(boolean valid);

}
