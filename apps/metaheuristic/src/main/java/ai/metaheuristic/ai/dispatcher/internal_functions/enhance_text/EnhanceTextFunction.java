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

package ai.metaheuristic.ai.dispatcher.internal_functions.enhance_text;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;
import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getNameForVariable;
import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getVariables;

/**
 * @author Sergio Lissner
 * Date: 5/17/2023
 * Time: 4:23 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class EnhanceTextFunction implements InternalFunction {

    public static final String TEXT = "text";

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final VariableTxService variableTxService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getCode() {
        return Consts.MH_ENHANCE_TEXT_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_ENHANCE_TEXT_FUNCTION;
    }

    public boolean isScenarioCompatible() {
        return true;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.outputs.isEmpty()) {
            throw new InternalFunctionException(variable_not_found, "515.040 output variable not found for task #" + taskId);
        }

        String text = MetaUtils.getValue(taskParamsYaml.task.metas, TEXT);
        if (S.b(text)) {
            throw new InternalFunctionException(meta_not_found, "515.080 meta '"+ TEXT +"' wasn't found or it's blank");
        }

        final List<String> variables = getVariables(text, false);
        for (String variable : variables) {
            String varName = getNameForVariable(variable);
            String value = internalFunctionVariableService.getValueOfVariable(simpleExecContext.execContextId, taskContextId, varName);
            if (value==null) {
                throw new InternalFunctionException(data_not_found, "515.120 data wasn't found, variable: "+variable+", normalized: " + varName);
            }
            text = StringUtils.replaceEach(text, new String[]{"[[" + variable + "]]", "{{" + variable + "}}"}, new String[]{value, value});
        }

        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        final String finalText = text;
        VariableSyncService.getWithSyncVoid(outputVariable.id, ()-> variableTxService.storeStringInVariable(outputVariable, finalText));

        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        //noinspection unused
        int i=0;
    }
}
