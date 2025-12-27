/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;

/**
 * @author Serge
 * Date: 12/11/2019
 * Time: 3:21 PM
 */
public class TaskParamsUtils {

    public static TaskParamsYaml.FunctionConfig toFunctionConfig(FunctionConfigYaml src) {
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig();
        trg.checksumMap = src.system!=null ? src.system.checksumMap : null;
        trg.code = src.function.code;
        trg.env = src.function.env;
        trg.file = src.function.file;
        trg.git = src.function.git;
        if (src.function.metas!=null) {
            trg.metas.addAll(src.function.metas);
        }
        trg.params = src.function.params;
        trg.sourcing = src.function.sourcing;
        trg.type = src.function.type;
        return trg;
    }
}
