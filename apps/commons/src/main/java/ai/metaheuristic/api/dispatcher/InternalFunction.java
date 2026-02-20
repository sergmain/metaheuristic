/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;

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

    default boolean isScenarioCompatible() {
        return false;
    }

    default boolean isCachable() {
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
    void process(ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml);
}
