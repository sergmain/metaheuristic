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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.TaskParamsUtils;

/**
 * @author Serge
 * Date: 11/25/2020
 * Time: 6:12 PM
 */
public class FunctionUtils {

    public static ReplicationApiData.FunctionShortConfig to(Function f) {

        ReplicationApiData.FunctionShortConfig fsc = new ReplicationApiData.FunctionShortConfig();

        TaskParamsYaml.FunctionConfig fc = TaskParamsUtils.toFunctionConfig(f.getFunctionConfigYaml());

        fsc.code = fc.code;
        fsc.file = fc.file;
        fsc.checksumMap = fc.checksumMap;
        fsc.sourcing = fc.sourcing;
        fsc.metas.addAll(fc.metas);

        return fsc;
    }

}
