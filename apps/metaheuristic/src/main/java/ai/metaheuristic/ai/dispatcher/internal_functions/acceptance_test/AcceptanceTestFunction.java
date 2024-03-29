/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.SubProcessesTxService;
import ai.metaheuristic.ai.dispatcher.internal_functions.api_call.ApiCallService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.general_error;
import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.meta_not_found;

/**
 * @author Sergio Lissner
 * Date: 5/23/2023
 * Time: 11:40 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AcceptanceTestFunction implements InternalFunction {

    private final ApplicationEventPublisher eventPublisher;
    private final ApiCallService apiCallService;
    private final SubProcessesTxService subProcessesTxService;

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

        executeAcceptanceTest(simpleExecContext, taskId, taskContextId, taskParamsYaml);

        ExecContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                ExecContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                        subProcessesTxService.processSubProcesses(simpleExecContext, taskId, taskParamsYaml)));

        //noinspection unused
        int i=0;
    }

    private void executeAcceptanceTest(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml) {
        String expected = MetaUtils.getValue(taskParamsYaml.task.metas, Consts.EXPECTED);
        if (S.b(expected)) {
            throw new InternalFunctionException(meta_not_found, "514.040 meta '" + Consts.EXPECTED + "' wasn't found or it's blank");
        }

        ProviderData.QuestionAndAnswer answer = apiCallService.callApi(simpleExecContext, taskId, taskContextId, taskParamsYaml);
        try {
            if (answer.status()==EnumsApi.OperationStatus.ERROR) {
                throw new InternalFunctionException(general_error, "514.080 error querying API: " + answer.error());
            }
            String s = answer.a()!=null && !answer.a().processedAnswer.rawAnswerFromAPI().type().binary ? answer.a().processedAnswer.answer() : null;
            if (S.b(s)) {
                throw new InternalFunctionException(general_error, "514.120 answer is empty");
            }
            if (!validateAnswer(expected, s)) {
                throw new InternalFunctionException(general_error, "514.160 Expected: "+expected+", but result is: " + s);
            }
        }
        finally {
            eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
        }
    }

    public static boolean validateAnswer(String expected, String actual) {
        final String stripped = actual.strip();

        for (String s : StringUtils.split(expected, "||")) {
            if (stripped.equals(s.strip())) {
                return true;
            }
        }
        return false;
    }
}
