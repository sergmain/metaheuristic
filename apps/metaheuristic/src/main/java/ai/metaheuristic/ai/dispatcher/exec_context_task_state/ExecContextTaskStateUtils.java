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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.api.EnumsApi;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 9/27/2023
 * Time: 9:52 AM
 */
public class ExecContextTaskStateUtils {

    public static long getCountUnfinishedTasks(ExecContextTaskState execContextTaskState) {
        return execContextTaskState.getExecContextTaskStateParamsYaml().states.entrySet()
                .stream()
                .filter(o -> o.getValue()== EnumsApi.TaskExecState.NONE || o.getValue()== EnumsApi.TaskExecState.IN_PROGRESS || o.getValue()== EnumsApi.TaskExecState.CHECK_CACHE)
                .count();
    }

    public static List<Long> getUnfinishedTaskVertices(ExecContextTaskState execContextTaskState) {
        return geTaskVertices(execContextTaskState, EnumsApi.TaskExecState.NONE, EnumsApi.TaskExecState.IN_PROGRESS, EnumsApi.TaskExecState.CHECK_CACHE);
    }

    public static List<Long> getFinishedTaskVertices(ExecContextTaskState execContextTaskState) {
        return geTaskVertices(execContextTaskState, EnumsApi.TaskExecState.OK, EnumsApi.TaskExecState.ERROR, EnumsApi.TaskExecState.ERROR_WITH_RECOVERY, EnumsApi.TaskExecState.SKIPPED);
    }

    public static List<Long> geTaskVertices(ExecContextTaskState execContextTaskState, EnumsApi.TaskExecState... taskExecStates) {
        return execContextTaskState.getExecContextTaskStateParamsYaml().states.entrySet()
                .stream()
                .filter(o -> isState(o.getValue(), taskExecStates))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public static boolean isState(EnumsApi.TaskExecState state, EnumsApi.TaskExecState... taskExecStates) {
        for (EnumsApi.TaskExecState taskExecState : taskExecStates) {
            if (state==taskExecState) {
                return true;
            }
        }
        return false;
    }
}
