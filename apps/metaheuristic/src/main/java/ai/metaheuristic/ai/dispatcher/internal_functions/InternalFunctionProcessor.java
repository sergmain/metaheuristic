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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.commons.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:47 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class InternalFunctionProcessor {

    private final InternalFunctionRegisterService internalFunctionRegisterService;

    /**
     *
     * @param simpleExecContext
     * @param taskId
     * @param internalContextId
     * @param taskParamsYaml
     *
     * @return boolean is that task a long-running task?
     */
    public boolean process(ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String internalContextId, TaskParamsYaml taskParamsYaml) {

        InternalFunction internalFunction = internalFunctionRegisterService.get(taskParamsYaml.task.function.code);
        if (internalFunction==null) {
            throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.function_not_found));
        }
        ArtifactCleanerAtDispatcher.setBusy();
        try {
            internalFunction.process(simpleExecContext, taskId, internalContextId, taskParamsYaml);
            return internalFunction.isLongRunning();
        }
        catch(InternalFunctionException e) {
            throw e;
        }
        catch(Throwable th) {
            String es = "#977.060 system error while processing internal function '" + internalFunction.getCode() + "', error: " + th.getMessage();
            log.error(es, th);
            throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        finally {
            ArtifactCleanerAtDispatcher.notBusy();
        }
    }
}
