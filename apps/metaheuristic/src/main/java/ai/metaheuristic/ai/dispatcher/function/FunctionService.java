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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionRuntimeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionService {

    private final FunctionCache functionCache;
    private final FunctionDataService functionDataService;

    @Transactional
    public void processFunctionStates(Long processorId, KeepAliveRequestParamYaml.FunctionDownloadStatuses functionDownloadStatus) {

/*
        for (KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status status : params.functions.statuses) {
            Function func = functions.stream().filter(f->f.code.equals(status.code)).findFirst().orElse(null);
            if (func==null) {
                continue;
            }
            FunctionRuntimeParamsYaml runtimeParams = func.getFunctionRuntimeParamsYaml();
            for (KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status st : params.functions.statuses) {
                String processorIdsAsStr = runtimeParams.states.computeIfAbsent(st.state, (k)->"");
                String[] processorIds = StringUtils.split(processorIdsAsStr, ",");
                KeepAliveRequestParamYaml.Processor processor = params.processor; {
                    if (processor.processorCommContext==null || processor.processorCommContext.processorId==null) {
                        return false;
                    }
                    String codeId = processor.processorCommContext.processorId.toString();
                    if (Arrays.stream(processorIds).noneMatch(o->o.equals(codeId))) {
                        return true;
                    }
                }
            }
        }
        return false;
*/

    }

    @Transactional
    public Void deleteFunction(Long functionId, String functionCode) {
        functionCache.delete(functionId);
        functionDataService.deleteByFunctionCode(functionCode);
        return null;
    }

    @Transactional
    public Function persistFunction(FunctionConfigYaml functionConfig, @Nullable InputStream inputStream, long size) {
        Function function = new Function();
        function.code = functionConfig.code;
        function.type = functionConfig.type!=null ? functionConfig.type : "";
        function.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(functionConfig);

        String functionCode = function.getCode();
        function = functionCache.save(function);
        if (inputStream!=null) {
            functionDataService.save(inputStream, size, functionCode);
        }
        return function;
    }

}
