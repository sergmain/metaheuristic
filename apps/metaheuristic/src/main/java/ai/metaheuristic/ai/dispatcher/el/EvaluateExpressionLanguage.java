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

package ai.metaheuristic.ai.dispatcher.el;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.system_error;

/**
 * @author Serge
 * Date: 6/22/2021
 * Time: 10:17 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class EvaluateExpressionLanguage {

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
        public final VariableRepository variableRepository;
        public final ExecContextVariableService execContextVariableService;
        public final Consumer<SimpleVariable> setAsNullFunction;

        public MhEvalContext(String taskContextId, Long execContextId, InternalFunctionVariableService internalFunctionVariableService,
                             GlobalVariableService globalVariableService, VariableService variableService,
                             ExecContextVariableService execContextVariableService, VariableRepository variableRepository,
                             Consumer<SimpleVariable> setAsNullFunction
                             ) {
            this.taskContextId = taskContextId;
            this.execContextId = execContextId;
            this.internalFunctionVariableService = internalFunctionVariableService;
            this.globalVariableService = globalVariableService;
            this.variableService = variableService;
            this.execContextVariableService = execContextVariableService;
            this.variableRepository = variableRepository;
            this.setAsNullFunction = setAsNullFunction;
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
                public boolean canRead(EvaluationContext context, @Nullable Object target, String name) {
                    return true;
                }

                @Override
                public TypedValue read(EvaluationContext context, @Nullable Object target, String name) {
                    VariableUtils.VariableHolder variableHolder = getVariableHolder(name);
                    TypeDescriptor typeDescriptor = new TypeDescriptor(
                            ResolvableType.forClass(VariableUtils.VariableHolder.class), VariableUtils.VariableHolder.class, null);

                    return new TypedValue(variableHolder, typeDescriptor);
                }

                @Override
                public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) {
                    return true;
                }

                @SuppressWarnings("ConstantConditions")
                @Override
                public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) {
                    VariableUtils.VariableHolder variableHolderOutput = getVariableHolder(name);
                    if (variableHolderOutput.globalVariable!=null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#509.030 global variable '"+ name+"' can't be used as output variable"));
                    }
                    if (variableHolderOutput.variable==null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#509.035 variable '"+ name+"' wasn't found"));
                    }

                    if (newValue==null) {
                        setAsNullFunction.accept(variableHolderOutput.variable);
                        return;
                    }

                    VariableUtils.VariableHolder variableHolderInput = null;
                    Integer intValue = null;
                    String strValue = null;
                    if (newValue instanceof VariableUtils.VariableHolder){
                        variableHolderInput = (VariableUtils.VariableHolder) newValue;
                    }
                    else if (newValue instanceof Integer) {
                        intValue = (Integer) newValue;
                    }
                    else if (newValue instanceof String) {
                        strValue = (String)newValue;
                    }
                    else if (newValue instanceof Boolean) {
                        strValue = ""+newValue;
                    }
                    else {
                        throw new InternalFunctionException(system_error, "#509.025 not supported type: " + newValue.getClass());
                    }
                    try {
                        if (variableHolderInput!=null) {
                            File tempDir = null;
                            try {
                                tempDir = DirUtils.createMhTempDir("mh-evaluation-");
                                if (tempDir == null) {
                                    throw new InternalFunctionException(system_error, "#509.050 can't create a temporary file");
                                }
                                File tempFile = File.createTempFile("input-", ".bin", tempDir);
                                if (variableHolderInput.globalVariable != null) {
                                    globalVariableService.storeToFileWithTx(variableHolderInput.globalVariable.id, tempFile);
                                } else if (variableHolderInput.variable != null) {
                                    variableService.storeToFileWithTx(variableHolderInput.variable.id, tempFile);
                                } else {
                                    throw new InternalFunctionException(system_error, "#509.052 both local and global variables are null");
                                }
                                try (InputStream is = new FileInputStream(tempFile)) {
                                    variableService.updateWithTx(is, tempFile.length(), variableHolderOutput.variable.id);
                                }
                            } finally {
                                DirUtils.deleteAsync(tempDir);
                            }
                            return;
                        }

                        byte[] bytes;
                        if (intValue!=null) {
                            bytes = intValue.toString().getBytes();
                        }
                        else if (strValue!=null) {
                            bytes = strValue.getBytes();
                        }
                        else {
                            throw new InternalFunctionException(system_error, "#509.025 not supported type: " + newValue.getClass());
                        }

                        try (InputStream is = new ByteArrayInputStream(bytes)) {
                            variableService.storeData(is, bytes.length, variableHolderOutput.variable.id, null);
                        }
                        variableHolderOutput.variable.inited = true;
                    }
                    catch (InternalFunctionException e) {
                        throw e;
                    }
                    catch (Throwable th) {
                        final String es = "#509.055 error " + th.getMessage();
                        log.error(es, th);
                        throw new InternalFunctionException(system_error, es);
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
                    throw new NotImplementedException("resolver not configured for " + name);
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
                    //noinspection ConstantConditions
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
                    if (sourceType==null || value==null) {
                        return null;
                    }
                    if (sourceType.getObjectType().equals(targetType.getObjectType())) {
                        return value;
                    }

                    if (targetType.getObjectType().equals(Boolean.class)) {
                        if (sourceType.getObjectType().equals(VariableUtils.VariableHolder.class)) {
                            return getValueBoolean(value);
                        }
                    }
                    if (targetType.getObjectType().equals(Integer.class)) {
                        if (sourceType.getObjectType().equals(VariableUtils.VariableHolder.class)) {
                            return getValueInteger(value);
                        }
                    }
                    throw new NotImplementedException("Not yet, srcType: "+sourceType.getObjectType().getSimpleName()+", trgType: " + targetType.getObjectType().getSimpleName());
                }
            };
//            return new StandardTypeConverter();
        }

        @Override
        public TypeComparator getTypeComparator() {
            return new TypeComparator() {
                @Override
                public boolean canCompare(@Nullable Object firstObject, @Nullable Object secondObject) {
                    return firstObject!=null && secondObject!=null;
                }

                @Override
                public int compare(@Nullable Object firstObject, @Nullable Object secondObject) throws EvaluationException {
                    if (firstObject==null || secondObject==null) {
                        throw new EvaluationException("(firstObject==null || secondObject==null)");
                    }
                    Integer firstValue = getValueInteger(firstObject);
                    Integer secondValue = getValueInteger(secondObject);
                    final int compare = firstValue.compareTo(secondValue);
                    return compare;
                }
            };
        }

        @Override
        public OperatorOverloader getOperatorOverloader() {
            OperatorOverloader ool = new OperatorOverloader() {
                @Override
                public boolean overridesOperation(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) throws EvaluationException {
                    return isOkClass(leftOperand) && isOkClass(rightOperand);
                }

                @Override
                public Object operate(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) throws EvaluationException {
                    if (leftOperand==null || rightOperand==null) {
                        throw new InternalFunctionException(
                                new InternalFunctionData.InternalFunctionProcessingResult(system_error,
                                        "#509.100 (leftOperand==null || rightOperand==null)"));
                    }
                    Integer leftValue = getValueInteger(leftOperand);
                    Integer rightValue = getValueInteger(rightOperand);
                    switch (operation) {
                        case ADD:
                            return leftValue + rightValue;
                        case SUBTRACT:
                            return leftValue - rightValue;
                        case DIVIDE:
                            return leftValue / rightValue;
                        case MULTIPLY:
                            return leftValue * rightValue;
                        case MODULUS:
                            return leftValue % rightValue;
                        case POWER:
                            return leftValue ^ rightValue;
                    }
                    throw new EvaluationException(S.f("Not supported operation %s, left: %, right: %s",
                            operation, leftOperand.getClass(), rightOperand.getClass()));
                }
            };
            return ool;
        }

        private Integer getValueInteger(Object operand) {
            if (operand instanceof Integer) {
                return (Integer)operand;
            }
            if (!(operand instanceof VariableUtils.VariableHolder variableHolder)) {
                throw new EvaluationException("not supported type: " + operand.getClass());
            }

            String strValue = getAsString(variableHolder);
            return Integer.valueOf(strValue);
        }

        private Boolean getValueBoolean(Object operand) {
            if (operand instanceof Boolean) {
                return (Boolean)operand;
            }
            if (!(operand instanceof VariableUtils.VariableHolder variableHolder)) {
                throw new EvaluationException("not supported type: " + operand.getClass());
            }

            String strValue = getAsString(variableHolder);
            return Boolean.parseBoolean(strValue);
        }

        private String getAsString(VariableUtils.VariableHolder variableHolder) {
            if (variableHolder.notInited()) {
                throw new EvaluationException("(variableHolder.notInited())");
            }
            String strValue;
            if (variableHolder.variable!=null) {
                strValue = variableService.getVariableDataAsString(variableHolder.variable.id);
            }
            else if (variableHolder.globalVariable!=null) {
                strValue = globalVariableService.getVariableDataAsString(variableHolder.globalVariable.id);
            }
            else {
                throw new IllegalStateException("both are null");
            }
            return strValue;
        }

        @Override
        public void setVariable(String name, @Nullable Object o) {
            VariableUtils.VariableHolder variableHolder = getVariableHolder(name);
            throw new NotImplementedException("Not yet");
        }

        @Nullable
        @Override
        public Object lookupVariable(String name) {
            VariableUtils.VariableHolder variableHolder = getVariableHolder(name);
            return variableHolder;
        }

        private static boolean isOkClass(@Nullable Object op) {
            return op instanceof VariableUtils.VariableHolder || op instanceof Integer;
        }

        public VariableUtils.VariableHolder getVariableHolder(String name) {
            List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(
                    execContextId, taskContextId, name);
            if (holders.size()>1) {
                throw new InternalFunctionException(Enums.InternalFunctionProcessing.source_code_is_broken,
                                "#509.160 Too many variables with the same name at top-level context, name: "+ name);
            }

            VariableUtils.VariableHolder variableHolder = holders.get(0);
            return variableHolder;
        }
    }

    @Nullable
    public static Object evaluate(
            String taskContextId, String expression, Long execContextId, InternalFunctionVariableService internalFunctionVariableService,
            GlobalVariableService globalVariableService, VariableService variableService, ExecContextVariableService execContextVariableService,
            VariableRepository variableRepository, Consumer<SimpleVariable> setAsNullFunction
    ) {

        ExpressionParser parser = new SpelExpressionParser();

        EvaluateExpressionLanguage.MhEvalContext mhEvalContext = new EvaluateExpressionLanguage.MhEvalContext(
                taskContextId, execContextId, internalFunctionVariableService, globalVariableService, variableService,
                execContextVariableService, variableRepository, setAsNullFunction);

        Expression exp = parser.parseExpression(expression);
        try {
            Object obj = exp.getValue(mhEvalContext);
            return obj;
        }
        catch (EvaluationException e) {
            log.error("Error while evaluating exp: " + expression, e);
            throw e;
        }
    }
}
