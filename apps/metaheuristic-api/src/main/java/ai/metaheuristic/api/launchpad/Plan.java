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

package ai.metaheuristic.api.launchpad;

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 3:27 PM
 */
public interface Plan {
    Long getId();

    Integer getVersion();

    String getCode();

    long getCreatedOn();

    String getParams();

    boolean isLocked();

    boolean isValid();

    boolean isClean();

    void setId(Long id);

    void setVersion(Integer version);

    void setCode(String code);

    void setCreatedOn(long createdOn);

    void setParams(String params);

    void setLocked(boolean locked);

    void setValid(boolean valid);

    void setClean(boolean clean);
}
