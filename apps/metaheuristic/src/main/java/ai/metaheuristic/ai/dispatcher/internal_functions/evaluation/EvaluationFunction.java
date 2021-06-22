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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardOperatorOverloader;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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
    public final GlobalVariableService globalVariableService;
    public final VariableService variableService;
    public final ExecContextVariableService execContextVariableService;

    // https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions

    @AllArgsConstructor
    public static class MhContext {
        public final String taskContextId;
        public final Long execContextId;
    }

    public static class MhEvalContext implements EvaluationContext {
        public final String taskContextId;
        public final Long execContextId;
        public final InternalFunctionVariableService internalFunctionVariableService;
        public final GlobalVariableService globalVariableService;
        public final VariableService variableService;
        public final ExecContextVariableService execContextVariableService;

        public MhEvalContext(String taskContextId, Long execContextId, InternalFunctionVariableService internalFunctionVariableService, GlobalVariableService globalVariableService, VariableService variableService, ExecContextVariableService execContextVariableService) {
            this.taskContextId = taskContextId;
            this.execContextId = execContextId;
            this.internalFunctionVariableService = internalFunctionVariableService;
            this.globalVariableService = globalVariableService;
            this.variableService = variableService;
            this.execContextVariableService = execContextVariableService;
        }

        @Override
        public TypedValue getRootObject() {
            return new TypedValue(new MhContext(taskContextId, execContextId));
        }

        @Override
        public List<PropertyAccessor> getPropertyAccessors() {
            PropertyAccessor pa = new PropertyAccessor() {
                @Nullable
                @Override
                public Class<?>[] getSpecificTargetClasses() {
                    return null;
                }

                @Override
                public boolean canRead(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
                    return true;
                }

                @Override
                public TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
                    VariableUtils.VariableHolder variableHolder = getVariableHolder(name);
                    return new TypedValue(variableHolder);
                }

                @Override
                public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
                    return true;
                }

                @SuppressWarnings("ConstantConditions")
                @Override
                public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) throws AccessException {
                    if (newValue==null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#503.020 can't create a temporary file"));
                    }
                    VariableUtils.VariableHolder variableHolderInput = null;
                    Integer intValue = null;
                    if (newValue instanceof VariableUtils.VariableHolder){
                        variableHolderInput = (VariableUtils.VariableHolder) newValue;
                    }
                    else if (newValue instanceof Integer) {
                        intValue = (Integer) newValue;
                    }
                    else {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#503.025 not supported type: " + newValue.getClass()));
                    }
                    VariableUtils.VariableHolder variableHolderOutput = getVariableHolder(name);
                    if (variableHolderOutput.globalVariable!=null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#503.030 global variable '"+ name+"' can't be used as output variable"));
                    }
                    if (variableHolderOutput.variable==null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#503.035 variable '"+ name+"' wasn't found"));
                    }
                    try {
                        if (variableHolderInput!=null) {
                            File tempDir = null;
                            try {
                                tempDir = DirUtils.createTempDir("mh-evaluation-");
                                if (tempDir == null) {
                                    throw new InternalFunctionException(
                                            new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                                    "#503.050 can't create a temporary file"));
                                }
                                File tempFile = File.createTempFile("input-", ".bin", tempDir);
                                if (variableHolderInput.globalVariable != null) {
                                    globalVariableService.storeToFileWithTx(variableHolderInput.globalVariable.id, tempFile);
                                } else if (variableHolderInput.variable != null) {
                                    variableService.storeToFileWithTx(variableHolderInput.variable.id, tempFile);
                                } else {
                                    throw new InternalFunctionException(
                                            new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                                    "#503.052 both local and global variables are null"));
                                }
                                try (InputStream is = new FileInputStream(tempFile)) {
                                    variableService.updateWithTx(is, tempFile.length(), variableHolderOutput.variable.id);
                                }
                            } finally {
                                if (tempDir!=null) {
                                    FileUtils.deleteQuietly(tempDir);
                                }
                            }
                        }
                        else if (intValue!=null) {
                            byte[] bytes = intValue.toString().getBytes();
                            InputStream is = new ByteArrayInputStream(bytes);
                            variableService.storeData(is, bytes.length, variableHolderOutput.variable.id, null);
                        }
                        else {
                            throw new InternalFunctionException(
                                    new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                            "#503.025 not supported type: " + newValue.getClass()));
                        }
                    }
                    catch (InternalFunctionException e) {
                        throw e;
                    }
                    catch (Throwable th) {
                        final String es = "#503.055 error " + th.getMessage();
                        log.error(es, th);
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,es));
                    }
                    int i=0;
                }
            };
            return List.of(pa);
        }

        @Override
        public List<ConstructorResolver> getConstructorResolvers() {
            ConstructorResolver cr = new ConstructorResolver() {
                @Nullable
                @Override
                public ConstructorExecutor resolve(EvaluationContext context, String typeName, List<TypeDescriptor> argumentTypes) throws AccessException {
                    return null;
                }
            };
            return List.of(cr);
        }

        @Override
        public List<MethodResolver> getMethodResolvers() {
            MethodResolver mr = new MethodResolver() {
                @Nullable
                @Override
                public MethodExecutor resolve(EvaluationContext context, Object targetObject, String name, List<TypeDescriptor> argumentTypes) throws AccessException {
                    return null;
                }
            };
            return List.of(mr);
        }

        @Nullable
        @Override
        public BeanResolver getBeanResolver() {
            return new BeanResolver() {
                @Override
                public Object resolve(EvaluationContext context, String beanName) throws AccessException {
                    return null;
                }
            };
        }

        @Override
        public TypeLocator getTypeLocator() {
            return typeName -> String.class;
        }

        @Override
        public TypeConverter getTypeConverter() {
            return new TypeConverter() {
                @Override
                public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
                    return false;
                }

                @Nullable
                @Override
                public Object convertValue(@Nullable Object value, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
                    return null;
                }
            };
//            return new StandardTypeConverter();
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
            OperatorOverloader ool = new OperatorOverloader() {
                @Override
                public boolean overridesOperation(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) throws EvaluationException {
                    if (operation==Operation.ADD && leftOperand instanceof VariableUtils.VariableHolder && rightOperand instanceof Integer) {
                        return true;
                    }
                    return false;
                }

                @Override
                public Object operate(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) throws EvaluationException {
                    if (leftOperand==null || rightOperand==null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#503.100 can't create a temporary file"));
                    }
                    VariableUtils.VariableHolder variableHolderInput = (VariableUtils.VariableHolder) leftOperand;
                    if (variableHolderInput.variable==null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#503.120 variable is null"));
                    }

                    if (operation==Operation.ADD && rightOperand instanceof Integer) {
                        String strValue = variableService.getVariableDataAsString(variableHolderInput.variable.id);
                        int value = Integer.parseInt(strValue) + (Integer)rightOperand;
                        return value;
                    }
                    throw new EvaluationException(S.f("Not supported operation %s, left: %, right: %s",
                            operation, leftOperand.getClass(), rightOperand.getClass()));
                }
            };
            return ool;
//            new StandardOperatorOverloader();
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

        public VariableUtils.VariableHolder getVariableHolder(String name) {
            List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(
                    execContextId, taskContextId, name);
            if (holders.size()>1) {
                throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                                "#503.160 Too many variables with the same name at top-level context, name: "+ name));
            }

            VariableUtils.VariableHolder variableHolder = holders.get(0);
            if (variableHolder.variable==null) {
                throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                                "#503.200 local variable with name: "+ name +" wasn't found"));
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
                            "#503.300 meta '"+ EXPRESSION +"' wasn't found"));
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            source_code_not_found,"#503.320 sourceCode #"+simpleExecContext.sourceCodeId+" wasn't found"));
        }

        ExpressionParser parser = new SpelExpressionParser();

        MhEvalContext mhEvalContext = new MhEvalContext(
                taskContextId, simpleExecContext.execContextId, internalFunctionVariableService, globalVariableService, variableService, execContextVariableService);

        Expression exp = parser.parseExpression(expression);
        Object obj = exp.getValue(mhEvalContext);

        int i=0;
    }


}
