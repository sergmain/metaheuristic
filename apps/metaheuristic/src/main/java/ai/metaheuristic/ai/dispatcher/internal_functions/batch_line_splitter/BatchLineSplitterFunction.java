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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_line_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.commons.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:36 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BatchLineSplitterFunction implements InternalFunction {

    public static final String VARIABLE_FOR_SPLITTING = "variable-for-splitting";
    public static final String NUMBER_OF_LINES_PER_TASK = "number-of-lines-per-task";
    public static final String OUTPUT_VARIABLE = "output-variable";
    public static final String IS_ARRAY = "is-array";
    private final VariableTxService variableService;
    private final GlobalVariableTxService globalVariableService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final BatchLineSplitterTxService batchLineSplitterTxService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_LINE_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_LINE_SPLITTER_FUNCTION;
    }

    public boolean isScenarioCompatible() {
        return true;
    }

    public void process(
        ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
        TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        ArtifactCleanerAtDispatcher.setBusy();
        try {
            processInternal(simpleExecContext, taskId, taskContextId, taskParamsYaml);
        }
        finally {
            ArtifactCleanerAtDispatcher.notBusy();
        }
    }

    private void processInternal(
        ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
        TaskParamsYaml taskParamsYaml) {

        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, VARIABLE_FOR_SPLITTING);
        if (S.b(inputVariableName)) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.meta_not_found, "994.020 Meta 'variable-for-splitting' wasn't found");
        }
        // number-of-lines-per-task
        Long numberOfLines = MetaUtils.getLong(taskParamsYaml.task.metas, NUMBER_OF_LINES_PER_TASK);
        if (numberOfLines==null) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.meta_not_found, "994.025 Meta 'number-of-lines-per-task' wasn't found");
        }

        List<VariableUtils.VariableHolder> varHolders = internalFunctionVariableService.discoverVariables(simpleExecContext.execContextId, taskContextId, inputVariableName);
        if (varHolders.isEmpty()) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, "994.030 No input variable was found");
        }

        if (varHolders.size()>1) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, "994.040 Too many variables");
        }

        VariableUtils.VariableHolder variableHolder = varHolders.get(0);

        // If the input variable is nullified (e.g. atomic requirement with no children),
        // there's nothing to split — complete successfully with zero sub-tasks
        if (variableHolder.variable!=null && variableHolder.variable.nullified) {
            log.info("994.045 Input variable '{}' is nullified, producing zero sub-tasks", inputVariableName);
            return;
        }

        String content;
        try {
            if (variableHolder.variable!=null) {
                content = variableService.getVariableDataAsString(variableHolder.variable.id);
            }
            else if (variableHolder.globalVariable!=null) {
                content = globalVariableService.getVariableDataAsString(variableHolder.globalVariable.id);
            }
            else {
                throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, "994.060 Global variable and variable both are null");
            }
        }
        catch (InternalFunctionException e) {
            throw e;
        }
        catch (Throwable th) {
            final String es = "994.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }

        ExecContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                ExecContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                        batchLineSplitterTxService.createTasksTx(simpleExecContext, taskId, taskParamsYaml, numberOfLines, content)));
    }
}
