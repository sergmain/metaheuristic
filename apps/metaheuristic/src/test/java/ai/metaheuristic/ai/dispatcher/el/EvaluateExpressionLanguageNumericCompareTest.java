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

package ai.metaheuristic.ai.dispatcher.el;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that reproduce the bug where numeric variable comparisons with arithmetic
 * results fail because isStringVariableHolder() is too broad.
 *
 * The expression "currIndex > factorialOf - 1" fails because:
 * 1. SpEL evaluates factorialOf - 1 via OperatorOverloader, producing a raw Integer
 * 2. TypeComparator.compare() checks isStringComparison() for currIndex (VariableHolder) vs Integer
 * 3. isStringVariableHolder() returns true for currIndex (numeric string "3" is non-null)
 * 4. getValueString() is called on the Integer operand and throws
 *
 * @author Serge
 * Date: 3/14/2026
 */
class EvaluateExpressionLanguageNumericCompareTest {

    /**
     * Simplified Ctx that maps variable IDs to string values, mirroring the real Ctx.
     */
    private record Ctx(Function<Long, String> varFunc, Function<Long, String> globalFunc) {}

    /**
     * Creates a Variable bean with the given id and name, marked as inited.
     */
    private static Variable createVariable(Long id, String name) {
        Variable v = new Variable();
        v.id = id;
        v.name = name;
        v.inited = true;
        return v;
    }

    /**
     * Creates an EvaluationContext that mirrors MhEvalContext behavior:
     * - PropertyAccessor returns VariableHolder for variable names
     * - OperatorOverloader handles arithmetic on VariableHolder/Integer
     * - TypeComparator handles comparison between VariableHolder/Integer/Boolean/String
     *
     * This uses the same logic as the production code so we're testing the actual algorithm.
     */
    private static EvaluationContext createMhLikeContext(Map<String, Long> nameToId, Map<Long, String> idToValue) {
        Ctx ctx = new Ctx(idToValue::get, id -> null);

        return new EvaluationContext() {
            @Override
            public TypedValue getRootObject() {
                return new TypedValue(new Object());
            }

            @Override
            public List<PropertyAccessor> getPropertyAccessors() {
                PropertyAccessor pa = new PropertyAccessor() {
                    @Override
                    public Class<?>@Nullable [] getSpecificTargetClasses() { return null; }

                    @Override
                    public boolean canRead(EvaluationContext context, @Nullable Object target, String name) { return true; }

                    @Override
                    public TypedValue read(EvaluationContext context, @Nullable Object target, String name) {
                        Long id = nameToId.get(name);
                        if (id == null) {
                            throw new IllegalStateException("Unknown variable: " + name);
                        }
                        Variable v = createVariable(id, name);
                        VariableUtils.VariableHolder vh = new VariableUtils.VariableHolder(v);
                        TypeDescriptor td = new TypeDescriptor(
                                ResolvableType.forClass(VariableUtils.VariableHolder.class), VariableUtils.VariableHolder.class, null);
                        return new TypedValue(vh, td);
                    }

                    @Override
                    public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) { return false; }

                    @Override
                    public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) {
                        throw new UnsupportedOperationException("write not supported in test");
                    }
                };
                return List.of(pa);
            }

            @Override
            public List<ConstructorResolver> getConstructorResolvers() { return Collections.emptyList(); }

            @Override
            public List<MethodResolver> getMethodResolvers() { return Collections.emptyList(); }

            @Nullable
            @Override
            public BeanResolver getBeanResolver() { return null; }

            @Override
            public TypeLocator getTypeLocator() {
                return typeName -> { throw new EvaluationException("Type not found: " + typeName); };
            }

            @Override
            public TypeConverter getTypeConverter() {
                return new TypeConverter() {
                    @Override
                    public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
                        return sourceType != null && (targetType.getObjectType().equals(Boolean.class) || targetType.getObjectType().equals(Integer.class));
                    }

                    @Override
                    public @Nullable Object convertValue(@Nullable Object value, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
                        if (targetType.getObjectType().equals(Boolean.class)) {
                            if (sourceType != null && sourceType.getObjectType().equals(VariableUtils.VariableHolder.class)) {
                                return getValueBoolean(ctx, value);
                            }
                        }
                        if (targetType.getObjectType().equals(Integer.class)) {
                            if (sourceType != null && sourceType.getObjectType().equals(VariableUtils.VariableHolder.class)) {
                                return getValueInteger(ctx, value);
                            }
                        }
                        throw new EvaluationException("Cannot convert");
                    }
                };
            }

            @Override
            public TypeComparator getTypeComparator() {
                return new TypeComparator() {
                    @Override
                    public boolean canCompare(@Nullable Object firstObject, @Nullable Object secondObject) {
                        return firstObject != null && secondObject != null;
                    }

                    @Override
                    public int compare(@Nullable Object firstObject, @Nullable Object secondObject) throws EvaluationException {
                        if (firstObject == null || secondObject == null) {
                            throw new EvaluationException("null operands");
                        }
                        if (isBooleanComparison(ctx, firstObject, secondObject)) {
                            Boolean firstValue = getValueBoolean(ctx, firstObject);
                            Boolean secondValue = getValueBoolean(ctx, secondObject);
                            return firstValue.compareTo(secondValue);
                        }
                        // This is the buggy check — isStringComparison is too broad
                        if (isStringComparison(ctx, firstObject, secondObject)) {
                            String firstValue = getValueString(ctx, firstObject);
                            String secondValue = getValueString(ctx, secondObject);
                            return firstValue.compareTo(secondValue);
                        }
                        Integer firstValue = getValueInteger(ctx, firstObject);
                        Integer secondValue = getValueInteger(ctx, secondObject);
                        return firstValue.compareTo(secondValue);
                    }
                };
            }

            @Override
            public OperatorOverloader getOperatorOverloader() {
                return new OperatorOverloader() {
                    @Override
                    public boolean overridesOperation(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) {
                        return isOkClass(leftOperand) && isOkClass(rightOperand);
                    }

                    @Override
                    public Object operate(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) {
                        if (leftOperand == null || rightOperand == null) {
                            throw new EvaluationException("null operands");
                        }
                        Integer leftValue = getValueInteger(ctx, leftOperand);
                        Integer rightValue = getValueInteger(ctx, rightOperand);
                        return switch (operation) {
                            case ADD -> leftValue + rightValue;
                            case SUBTRACT -> leftValue - rightValue;
                            case DIVIDE -> leftValue / rightValue;
                            case MULTIPLY -> leftValue * rightValue;
                            case MODULUS -> leftValue % rightValue;
                            case POWER -> leftValue ^ rightValue;
                        };
                    }
                };
            }

            @Nullable
            @Override
            public Object lookupVariable(String name) {
                Long id = nameToId.get(name);
                if (id == null) return null;
                Variable v = createVariable(id, name);
                return new VariableUtils.VariableHolder(v);
            }

            @Override
            public void setVariable(String name, @Nullable Object value) {
                throw new UnsupportedOperationException("setVariable not supported in test");
            }
        };
    }

    // ---- Static methods copied from production code (EvaluateExpressionLanguage) ----
    // These are the methods under test. The bug is in isStringVariableHolder.

    private static boolean isBooleanComparison(Ctx ctx, Object first, Object second) {
        return first instanceof Boolean || second instanceof Boolean
                || isBooleanVariableHolder(ctx, first) || isBooleanVariableHolder(ctx, second);
    }

    private static boolean isStringComparison(Ctx ctx, Object first, Object second) {
        return first instanceof String || second instanceof String
                || isStringVariableHolder(ctx, first) || isStringVariableHolder(ctx, second);
    }

    private static boolean isBooleanVariableHolder(Ctx ctx, Object obj) {
        if (!(obj instanceof VariableUtils.VariableHolder vh)) return false;
        if (vh.notInited()) return false;
        String strValue;
        if (vh.variable != null) strValue = ctx.varFunc.apply(vh.variable.id);
        else if (vh.globalVariable != null) strValue = ctx.globalFunc.apply(vh.globalVariable.id);
        else return false;
        return "true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue);
    }

    private static boolean isStringVariableHolder(Ctx ctx, Object obj) {
        if (!(obj instanceof VariableUtils.VariableHolder vh)) return false;
        if (vh.notInited()) return false;
        String strValue;
        if (vh.variable != null) strValue = ctx.varFunc.apply(vh.variable.id);
        else if (vh.globalVariable != null) strValue = ctx.globalFunc.apply(vh.globalVariable.id);
        else return false;
        if (strValue == null) return false;
        // Exclude values that are parseable as boolean or integer — those are not "string" comparisons
        if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) return false;
        try { Integer.parseInt(strValue); return false; } catch (NumberFormatException e) { /* not a number */ }
        return true;
    }

    private static Integer getValueInteger(Ctx ctx, Object operand) {
        if (operand instanceof Integer) return (Integer) operand;
        if (!(operand instanceof VariableUtils.VariableHolder variableHolder)) {
            throw new EvaluationException("not supported type: " + operand.getClass());
        }
        String strValue = getAsString(ctx, variableHolder);
        return Integer.valueOf(strValue);
    }

    private static Boolean getValueBoolean(Ctx ctx, Object operand) {
        if (operand instanceof Boolean) return (Boolean) operand;
        if (!(operand instanceof VariableUtils.VariableHolder variableHolder)) {
            throw new EvaluationException("not supported type: " + operand.getClass());
        }
        String strValue = getAsString(ctx, variableHolder);
        return Boolean.parseBoolean(strValue);
    }

    private static String getValueString(Ctx ctx, Object operand) {
        if (operand instanceof String) return (String) operand;
        if (operand instanceof Integer i) return i.toString();
        if (!(operand instanceof VariableUtils.VariableHolder variableHolder)) {
            throw new EvaluationException("not supported type: " + operand.getClass());
        }
        String strValue = getAsString(ctx, variableHolder);
        return strValue;
    }

    private static String getAsString(Ctx ctx, VariableUtils.VariableHolder variableHolder) {
        if (variableHolder.notInited()) throw new EvaluationException("variableHolder.notInited()");
        String strValue;
        if (variableHolder.variable != null) strValue = ctx.varFunc.apply(variableHolder.variable.id);
        else if (variableHolder.globalVariable != null) strValue = ctx.globalFunc.apply(variableHolder.globalVariable.id);
        else throw new IllegalStateException("both are null");
        return strValue;
    }

    private static boolean isOkClass(@Nullable Object o) {
        return o instanceof Integer || o instanceof VariableUtils.VariableHolder;
    }

    // ---- Tests ----

    /**
     * Reproduces the exact bug: "currIndex > factorialOf - 1" where both variables hold numeric strings.
     * factorialOf - 1 produces a raw Integer via OperatorOverloader.
     * Then the comparison currIndex > (Integer) triggers isStringComparison which incorrectly returns true
     * because isStringVariableHolder returns true for a numeric string value.
     * getValueString then throws on the Integer operand.
     */
    @Test
    void test_numericComparison_withArithmetic_shouldNotRouteToStringComparison() {
        // currIndex=3, factorialOf=5 -> expression: currIndex > factorialOf - 1 -> 3 > 4 -> false
        Map<String, Long> nameToId = Map.of("currIndex", 1L, "factorialOf", 2L);
        Map<Long, String> idToValue = Map.of(1L, "3", 2L, "5");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        // This should NOT throw — it should evaluate to false (3 > 4 is false)
        Object result = parser.parseExpression("currIndex > factorialOf - 1").getValue(evalCtx);
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void test_numericComparison_greaterThan_true() {
        // currIndex=5, factorialOf=5 -> expression: currIndex > factorialOf - 1 -> 5 > 4 -> true
        Map<String, Long> nameToId = Map.of("currIndex", 1L, "factorialOf", 2L);
        Map<Long, String> idToValue = Map.of(1L, "5", 2L, "5");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("currIndex > factorialOf - 1").getValue(evalCtx);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_numericComparison_lessThan_withArithmetic() {
        // currIndex=2, factorialOf=5 -> expression: currIndex < factorialOf -> 2 < 5 -> true
        Map<String, Long> nameToId = Map.of("currIndex", 1L, "factorialOf", 2L);
        Map<Long, String> idToValue = Map.of(1L, "2", 2L, "5");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("currIndex < factorialOf").getValue(evalCtx);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_numericComparison_equality_withArithmetic() {
        // currIndex=4, factorialOf=5 -> expression: currIndex == factorialOf - 1 -> 4 == 4 -> true
        Map<String, Long> nameToId = Map.of("currIndex", 1L, "factorialOf", 2L);
        Map<Long, String> idToValue = Map.of(1L, "4", 2L, "5");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("currIndex == factorialOf - 1").getValue(evalCtx);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_stringComparison_stillWorks() {
        // amendmentStatus="ACTIVE" -> expression: amendmentStatus == 'ACTIVE' -> true
        Map<String, Long> nameToId = Map.of("amendmentStatus", 1L);
        Map<Long, String> idToValue = Map.of(1L, "ACTIVE");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("amendmentStatus == 'ACTIVE'").getValue(evalCtx);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_stringComparison_notEqual() {
        Map<String, Long> nameToId = Map.of("amendmentStatus", 1L);
        Map<Long, String> idToValue = Map.of(1L, "OBSOLETE");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("amendmentStatus == 'ACTIVE'").getValue(evalCtx);
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void test_booleanComparison_stillWorks() {
        Map<String, Long> nameToId = Map.of("flag", 1L);
        Map<Long, String> idToValue = Map.of(1L, "true");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("flag == true").getValue(evalCtx);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_numericComparison_additionOnBothSides() {
        // a=3, b=2, c=1 -> a + c > b -> 4 > 2 -> true
        Map<String, Long> nameToId = Map.of("a", 1L, "b", 2L, "c", 3L);
        Map<Long, String> idToValue = Map.of(1L, "3", 2L, "2", 3L, "1");

        var evalCtx = createMhLikeContext(nameToId, idToValue);
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("a + c > b").getValue(evalCtx);
        assertEquals(Boolean.TRUE, result);
    }
}
