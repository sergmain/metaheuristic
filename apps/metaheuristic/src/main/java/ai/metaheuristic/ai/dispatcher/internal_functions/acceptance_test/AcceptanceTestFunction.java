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

package ai.metaheuristic.ai.dispatcher.internal_functions.acceptance_test;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.api_call.ApiCallService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.meta_not_found;

/**
 * @author Sergio Lissner
 * Date: 5/23/2023
 * Time: 11:40 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class AcceptanceTestFunction implements InternalFunction {

    private final ApplicationEventPublisher eventPublisher;
    private final ApiCallService apiCallService;

    @Override
    public String getCode() {
        return Consts.MH_ACCEPTANCE_TEST_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_ACCEPTANCE_TEST_FUNCTION;
    }

    public boolean isScenarioCompatible() {
        return true;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        String prompt = MetaUtils.getValue(taskParamsYaml.task.metas, Consts.EXPECTED);
        if (S.b(prompt)) {
            throw new InternalFunctionException(meta_not_found, "514.040 meta '" + Consts.EXPECTED + "' wasn't found or it's blank");
        }

        ProviderData.QuestionAndAnswer answer = apiCallService.callApi(simpleExecContext, taskId, taskContextId, taskParamsYaml);
        String s = answer.a();

        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        //noinspection unused
        int i=0;
    }
}
