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

package ai.metaheuristic.ai.dispatcher.internal_functions.evaluation;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.el.EvaluateExpressionLanguage;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.meta_not_found;
import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.source_code_not_found;

/**
 * @author Serge
 * Date: 6/21/2021
 * Time: 6:47 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EvaluationFunction implements InternalFunction {

    private static final String EXPRESSION = "expression";

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final SourceCodeCache sourceCodeCache;
    private final GlobalVariableTxService globalVariableService;
    private final VariableTxService variableTxService;
    private final VariableRepository variableRepository;

    @Override
    public String getCode() {
        return Consts.MH_EVALUATION_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_EVALUATION_FUNCTION;
    }

    @Override
    public void process(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        String expression = MetaUtils.getValue(taskParamsYaml.task.metas, EXPRESSION);
        if (S.b(expression)) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult( meta_not_found,
                            "503.030 meta '"+ EXPRESSION +"' wasn't found"));
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            source_code_not_found,"503.060 sourceCode #"+simpleExecContext.sourceCodeId+" wasn't found"));
        }

        // in EvaluateExpressionLanguage.evaluate() we need only to use variableService.setVariableAsNull(v.id)
        // because mh.evaluate doesn't have any output variables
        Object obj = EvaluateExpressionLanguage.evaluate(
            simpleExecContext.execContextId, taskId, taskContextId, expression,
            this.internalFunctionVariableService, this.globalVariableService, this.variableTxService, variableRepository,
                (v) -> VariableSyncService.getWithSyncVoidForCreation(v.id,
                        ()-> variableTxService.setVariableAsNull(taskId, v.id)));

        if (log.isDebugEnabled()) {
            log.debug("mh.evaluation, expression: {} result: {}", expression, obj);
        }
        int i=0;
    }

}
