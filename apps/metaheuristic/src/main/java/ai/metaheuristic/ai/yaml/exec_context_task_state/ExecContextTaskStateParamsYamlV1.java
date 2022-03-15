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

package ai.metaheuristic.ai.yaml.exec_context_task_state;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:33 AM
 */
@Data
public class ExecContextTaskStateParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public final Map<Long, EnumsApi.TaskExecState> states = new HashMap<>();

    public final Map<Long, Integer> triesWasMade = new HashMap<>();
}
