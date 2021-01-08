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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import lombok.SneakyThrows;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 11/14/2020
 * Time: 8:20 PM
 */
public class ExecContextUtils {

    @SneakyThrows
    public static ExecContextApiData.ExecContextTasksStatesInfo getExecContextTasksStatesInfo(@Nullable String tasksStatesInfo) {
        ExecContextApiData.ExecContextTasksStatesInfo info;
        if (S.b(tasksStatesInfo)) {
            info = new ExecContextApiData.ExecContextTasksStatesInfo();
        }
        else {
            info = JsonUtils.getMapper().readValue(tasksStatesInfo, ExecContextApiData.ExecContextTasksStatesInfo.class);
        }
        return info;
    }

}
