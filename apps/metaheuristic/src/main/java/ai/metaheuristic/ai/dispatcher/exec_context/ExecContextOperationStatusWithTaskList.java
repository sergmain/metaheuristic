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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Serge
 * Date: 7/15/2019
 * Time: 11:03 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecContextOperationStatusWithTaskList {
    public OperationStatusRest status;
    public Set<ExecContextData.TaskWithState> childrenTasks = new HashSet<>();

    public ExecContextOperationStatusWithTaskList(OperationStatusRest status) {
        this.status = status;
    }
}
