/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.springframework.lang.NonNull;

/**
 * @author Serge
 * Date: 12/11/2019
 * Time: 3:21 PM
 */
public class TaskParamsUtils {

    public static TaskParamsYaml.FunctionConfig toFunctionConfig(@NonNull FunctionConfigYaml src) {
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        trg.info.signed = src.info.signed;
        trg.info.length = src.info.length;
        trg.metas.addAll(src.metas);
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }
}
