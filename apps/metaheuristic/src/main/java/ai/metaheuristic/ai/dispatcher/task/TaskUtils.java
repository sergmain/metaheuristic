/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 5:51 AM
 */
public class TaskUtils {

    public static boolean gitUnavailable(TaskParamsYaml.TaskYaml task, boolean gitNotInstalled) {
        if (task.function.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
            return true;
        }
        for (TaskParamsYaml.FunctionConfig preFunction : task.preFunctions) {
            if (preFunction.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
                return true;
            }
        }
        for (TaskParamsYaml.FunctionConfig postFunction : task.postFunctions) {
            if (postFunction.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
                return true;
            }
        }
        return false;
    }


}
