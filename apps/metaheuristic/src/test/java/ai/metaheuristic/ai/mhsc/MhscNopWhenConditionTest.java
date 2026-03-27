package ai.metaheuristic.ai.mhsc;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Tests that the .mhsc 'when' ternary condition on mh.nop is correctly parsed
 * and that the SpEL evaluation of 'hasObjectives ? true : false' works correctly
 * for both "true" and "false" variable values.
 *
 * Reproduces the suspected bug where the condition always evaluates to true (never skipping).
 *
 * The test uses a minimal EvaluationContext that mimics MhEvalContext behavior:
 * variables are wrapped in a holder object, and TypeConverter handles
 * VariableHolder → Boolean conversion by reading the string value.
 *
 * @author Sergio Lissner
 * Date: 3/27/2026
 */
@Execution(CONCURRENT)
class MhscNopWhenConditionTest {

    /**
     * Simulates VariableUtils.VariableHolder — holds a string value
     * that SpEL must convert to Boolean for ternary condition evaluation.
     */
    record VarHolder(String value) {}

    private static final String MHSC_SOURCE = """
            source "test-nop-when-1.0" (strict) {
            
                variables {
                    <- projectCode
                }
            
                mh.init-var := internal mh.string-as-variable {
                    meta mapping = "mapping:\\n  - group: keys\\n    name: hasObjectives\\n    output: hasObjectives"
                    -> hasObjectives: ext=".txt"
                }
            
                mh.nop-condition-test := internal mh.nop {
                    when hasObjectives ? true : false
                    sequential {
                        mh.nop-inner := internal mh.nop {
                        }
                    }
                }
            }
            """;

    // ========== Test 1: .mhsc parsing ==========

    @Test
    void test_when_condition_parsed_from_mhsc() {
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);

        assertNotNull(graph);
        assertFalse(graph.processes.isEmpty());

        ExecContextParamsYaml.Process nopProcess = graph.processes.stream()
                .filter(p -> "mh.nop-condition-test".equals(p.processCode))
                .findFirst()
                .orElse(null);

        assertNotNull(nopProcess, "Should find mh.nop-condition-test process");
        assertNotNull(nopProcess.condition, "condition should not be null");
        assertFalse(nopProcess.condition.isBlank(), "condition should not be blank");

        System.out.println("Parsed condition: '" + nopProcess.condition + "'");

        assertTrue(nopProcess.condition.contains("hasObjectives"),
                "condition should reference 'hasObjectives', got: " + nopProcess.condition);
    }

    // ========== Test 2: SpEL ternary evaluation ==========

    /**
     * Tests that the ternary expression 'hasObjectives ? true : false' evaluates correctly
     * when the variable holds "true" — should return Boolean.TRUE (don't skip).
     */
    @Test
    void test_ternary_condition_with_true_value() {
        String condition = "hasObjectives ? true : false";
        Object result = evaluateCondition(condition, Map.of("hasObjectives", "true"));
        assertEquals(Boolean.TRUE, result,
                "Ternary 'hasObjectives ? true : false' should return TRUE when hasObjectives='true'");
    }

    /**
     * Tests that the ternary expression 'hasObjectives ? true : false' evaluates correctly
     * when the variable holds "false" — should return Boolean.FALSE (skip).
     *
     * This is the suspected bug: if this returns TRUE, the mh.nop never skips.
     */
    @Test
    void test_ternary_condition_with_false_value() {
        String condition = "hasObjectives ? true : false";
        Object result = evaluateCondition(condition, Map.of("hasObjectives", "false"));
        assertEquals(Boolean.FALSE, result,
                "Ternary 'hasObjectives ? true : false' should return FALSE when hasObjectives='false'");
    }

    /**
     * Tests bare variable reference: 'hasObjectives' (no ternary).
     * When variable holds "true", should return truthy (not skip).
     */
    @Test
    void test_bare_condition_with_true_value() {
        String condition = "hasObjectives";
        Object result = evaluateCondition(condition, Map.of("hasObjectives", "true"));
        assertNotNull(result);
        // Bare variable returns VarHolder, TaskWithInternalContextEventService extracts Boolean from it
        if (result instanceof VarHolder vh) {
            assertEquals("true", vh.value());
        } else if (result instanceof Boolean b) {
            assertTrue(b);
        }
    }

    /**
     * Tests bare variable reference: 'hasObjectives' (no ternary).
     * When variable holds "false", should return falsy (skip).
     */
    @Test
    void test_bare_condition_with_false_value() {
        String condition = "hasObjectives";
        Object result = evaluateCondition(condition, Map.of("hasObjectives", "false"));
        assertNotNull(result);
        if (result instanceof VarHolder vh) {
            assertEquals("false", vh.value());
        } else if (result instanceof Boolean b) {
            assertFalse(b);
        }
    }

    // ========== SpEL evaluation helper ==========

    /**
     * Evaluates a SpEL expression using an EvaluationContext that mimics MhEvalContext:
     * - Variables are resolved via PropertyAccessor (like MhEvalContext.getPropertyAccessors())
     * - VarHolder → Boolean conversion via TypeConverter (like MhEvalContext.getTypeConverter())
     */
    private static Object evaluateCondition(String expression, Map<String, String> variables) {
        ExpressionParser parser = new SpelExpressionParser();

        EvaluationContext ctx = new EvaluationContext() {
            private final Map<String, VarHolder> vars = new HashMap<>();
            {
                variables.forEach((k, v) -> vars.put(k, new VarHolder(v)));
            }

            @Override
            public TypedValue getRootObject() {
                return TypedValue.NULL;
            }

            @Override
            public List<PropertyAccessor> getPropertyAccessors() {
                return List.of(new PropertyAccessor() {
                    @Override
                    public Class<?>@Nullable [] getSpecificTargetClasses() { return null; }

                    @Override
                    public boolean canRead(EvaluationContext context, @Nullable Object target, String name) {
                        return vars.containsKey(name);
                    }

                    @Override
                    public TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
                        VarHolder vh = vars.get(name);
                        if (vh == null) {
                            throw new AccessException("Variable not found: " + name);
                        }
                        TypeDescriptor td = new TypeDescriptor(
                                ResolvableType.forClass(VarHolder.class), VarHolder.class, null);
                        return new TypedValue(vh, td);
                    }

                    @Override
                    public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) {
                        return false;
                    }

                    @Override
                    public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) {
                        throw new UnsupportedOperationException();
                    }
                });
            }

            @Override
            public List<ConstructorResolver> getConstructorResolvers() { return List.of(); }

            @Override
            public List<MethodResolver> getMethodResolvers() { return List.of(); }

            @Override
            public @Nullable BeanResolver getBeanResolver() { return null; }

            @Override
            public TypeLocator getTypeLocator() { return typeName -> String.class; }

            @Override
            public TypeConverter getTypeConverter() {
                return new TypeConverter() {
                    @Override
                    public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
                        if (sourceType == null) return false;
                        if (sourceType.getObjectType().equals(targetType.getObjectType())) return true;
                        if (sourceType.getObjectType().equals(VarHolder.class) && targetType.getObjectType().equals(Boolean.class)) return true;
                        return false;
                    }

                    @Override
                    public @Nullable Object convertValue(@Nullable Object value, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
                        if (value == null || sourceType == null) return null;
                        if (sourceType.getObjectType().equals(targetType.getObjectType())) return value;
                        if (value instanceof VarHolder vh && targetType.getObjectType().equals(Boolean.class)) {
                            return Boolean.parseBoolean(vh.value());
                        }
                        throw new IllegalStateException("Cannot convert " + sourceType + " to " + targetType);
                    }
                };
            }

            @Override
            public TypeComparator getTypeComparator() {
                return new TypeComparator() {
                    @Override
                    public boolean canCompare(@Nullable Object a, @Nullable Object b) {
                        return a != null && b != null;
                    }

                    @Override
                    public int compare(@Nullable Object a, @Nullable Object b) {
                        if (a == null || b == null) throw new EvaluationException("null operand");
                        Boolean first = toBoolean(a);
                        Boolean second = toBoolean(b);
                        return first.compareTo(second);
                    }

                    private Boolean toBoolean(Object obj) {
                        if (obj instanceof Boolean b) return b;
                        if (obj instanceof VarHolder vh) return Boolean.parseBoolean(vh.value());
                        if (obj instanceof String s) return Boolean.parseBoolean(s);
                        throw new EvaluationException("Cannot convert to Boolean: " + obj.getClass());
                    }
                };
            }

            @Override
            public OperatorOverloader getOperatorOverloader() {
                return new OperatorOverloader() {
                    @Override
                    public boolean overridesOperation(Operation op, @Nullable Object left, @Nullable Object right) { return false; }
                    @Override
                    public Object operate(Operation op, @Nullable Object left, @Nullable Object right) { throw new UnsupportedOperationException(); }
                };
            }

            @Override
            public void setVariable(String name, @Nullable Object value) {}

            @Override
            public @Nullable Object lookupVariable(String name) {
                return vars.get(name);
            }
        };

        Expression exp = parser.parseExpression(expression);
        return exp.getValue(ctx);
    }
}
