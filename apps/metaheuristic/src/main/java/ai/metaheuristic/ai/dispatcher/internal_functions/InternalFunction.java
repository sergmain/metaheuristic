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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:19 PM
 */
public interface InternalFunction {

    String getCode();

    String getName();

    default boolean isLongRunning() {
        return false;
    }

    /**
     * @param simpleExecContext
     * @param taskId
     * @param taskContextId
     * @param taskParamsYaml
     * @return
     *
     * @see ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor#process
     */
    void process(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml);
}
