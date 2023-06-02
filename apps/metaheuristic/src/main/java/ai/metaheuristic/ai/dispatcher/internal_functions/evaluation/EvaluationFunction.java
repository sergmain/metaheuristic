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

package ai.metaheuristic.ai.dispatcher.internal_functions.evaluation;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.el.EvaluateExpressionLanguage;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.VariableEntityManagerService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class EvaluationFunction implements InternalFunction {

    private static final String EXPRESSION = "expression";

    public final InternalFunctionVariableService internalFunctionVariableService;
    public final SourceCodeCache sourceCodeCache;
    public final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    public final SourceCodeRepository sourceCodeRepository;
    public final GlobalVariableService globalVariableService;
    public final VariableTxService variableTxService;
    public final VariableRepository variableRepository;
    public final ExecContextCache execContextCache;
    public final VariableTopLevelService variableTopLevelService;
    public final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    public final VariableEntityManagerService variableEntityManagerService;

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
                            "#503.300 meta '"+ EXPRESSION +"' wasn't found"));
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            source_code_not_found,"#503.320 sourceCode #"+simpleExecContext.sourceCodeId+" wasn't found"));
        }

        // in EvaluateExpressionLanguage.evaluate() we need only to use variableService.setVariableAsNull(v.id)
        // because mh.evaluate doesn't have any output variables
        Object obj = EvaluateExpressionLanguage.evaluate(
                taskContextId, expression, simpleExecContext.execContextId,
                this.internalFunctionVariableService, this.globalVariableService, this.variableTxService, variableRepository,
                variableEntityManagerService,
                (v) -> VariableSyncService.getWithSyncVoidForCreation(v.id,
                        ()-> variableTxService.setVariableAsNull(v.id)));

        System.out.println("mh.evaluation, expression: "+expression+", result:" + obj);
        int i=0;
    }

}
