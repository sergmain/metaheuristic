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

package ai.metaheuristic.ai.exceptions;

import lombok.Data;

/**
 * @author Serge
 * Date: 11/1/2020
 * Time: 1:57 AM
 */
public class InvalidateCacheProcessException extends RuntimeException {
    public Long execContextId;
    public Long taskId;
    public Long cacheProcessId;

    public InvalidateCacheProcessException(Long execContextId, Long taskId, Long cacheProcessId) {
        super();
        this.execContextId = execContextId;
        this.taskId = taskId;
        this.cacheProcessId = cacheProcessId;
    }

}
