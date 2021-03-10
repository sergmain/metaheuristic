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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:47 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class InternalFunctionProcessor {

    private final ExecContextSyncService execContextSyncService;
    private final InternalFunctionRegisterService internalFunctionRegisterService;

    public void process(ExecContextImpl execContext, TaskImpl task, String internalContextId, TaskParamsYaml taskParamsYaml) {
        execContextSyncService.checkWriteLockPresent(execContext.id);

        InternalFunction internalFunction = internalFunctionRegisterService.get(taskParamsYaml.task.function.code);
        if (internalFunction==null) {
            throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.function_not_found));
        }
        ExecContextParamsYaml expy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        try {
            internalFunction.process(execContext, task, internalContextId, expy.variables, taskParamsYaml);
        }
        catch(InternalFunctionException e) {
            throw e;
        }
        catch(Throwable th) {
            String es = "#977.060 system error while processing internal function '" + internalFunction.getCode() + "', error: " + th.getMessage();
            log.error(es, th);
            throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
    }
}
