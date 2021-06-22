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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardOperatorOverloader;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

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

    // https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions

    public static class MhEvalContext implements EvaluationContext {
        public final String taskContextId;
        public final Long execContextId;
        public final InternalFunctionVariableService internalFunctionVariableService;

        public MhEvalContext(String taskContextId, Long execContextId, InternalFunctionVariableService internalFunctionVariableService) {
            this.taskContextId = taskContextId;
            this.execContextId = execContextId;
            this.internalFunctionVariableService = internalFunctionVariableService;
        }

        @Override
        public TypedValue getRootObject() {
            return TypedValue.NULL;
        }

        @Override
        public List<PropertyAccessor> getPropertyAccessors() {
            return List.of();
        }

        @Override
        public List<ConstructorResolver> getConstructorResolvers() {
            return List.of();
        }

        @Override
        public List<MethodResolver> getMethodResolvers() {
            return List.of();
        }

        @Nullable
        @Override
        public BeanResolver getBeanResolver() {
            return null;
        }

        @Override
        public TypeLocator getTypeLocator() {
            return typeName -> String.class;
        }

        @Override
        public TypeConverter getTypeConverter() {
            return new StandardTypeConverter();
        }

        @Override
        public TypeComparator getTypeComparator() {
            return new TypeComparator() {
                @Override
                public boolean canCompare(@Nullable Object firstObject, @Nullable Object secondObject) {
                    return false;
                }

                @Override
                public int compare(@Nullable Object firstObject, @Nullable Object secondObject) throws EvaluationException {
                    return 0;
                }
            };
        }

        @Override
        public OperatorOverloader getOperatorOverloader() {
            return new StandardOperatorOverloader();
        }

        @Override
        public void setVariable(String name, @Nullable Object o) {
            VariableUtils.VariableHolder variableHolder = getVariableHolder(name);

        }

        @Nullable
        @Override
        public Object lookupVariable(String name) {
            VariableUtils.VariableHolder variableHolder = getVariableHolder(name);
            return variableHolder;
        }

        private VariableUtils.VariableHolder getVariableHolder(String name) {
            List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(
                    execContextId, taskContextId, name);
            if (holders.size()>1) {
                throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                                "#503.060 Too many variables with the same name at top-level context, name: "+ name));
            }

            VariableUtils.VariableHolder variableHolder = holders.get(0);
            if (variableHolder.variable==null) {
                throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                                "#503.100 local variable with name: "+ name +" wasn't found"));
            }
            return variableHolder;
        }
    }

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
                            "#503.200 meta '"+ EXPRESSION +"' wasn't found"));
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            source_code_not_found,"#503.220 sourceCode #"+simpleExecContext.sourceCodeId+" wasn't found"));
        }

        ExpressionParser parser = new SpelExpressionParser();

        MhEvalContext mhEvalContext = new MhEvalContext(taskContextId, simpleExecContext.execContextId, internalFunctionVariableService);

        Expression exp = parser.parseExpression(expression);
        Object obj = exp.getValue(mhEvalContext);
    }


}
