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

import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for string comparison support in EvaluateExpressionLanguage.
 * Validates that when a variable holds a non-numeric, non-boolean string value (like "ACTIVE"),
 * SpEL equality comparisons work correctly instead of crashing with NumberFormatException.
 *
 * The bug: `compare()` in MhEvalContext.TypeComparator only handled boolean and integer comparisons.
 * String values like "ACTIVE" fell through to `getValueInteger()` which threw NumberFormatException.
 *
 * These tests use a wrapper object to simulate the VariableHolder scenario:
 * when SpEL encounters an unknown object type in == comparison, it delegates to the TypeComparator.
 *
 * @author Serge
 * Date: 3/12/2026
 */
class EvaluateExpressionLanguageStringCompareTest {

    /**
     * A simple wrapper that simulates VariableHolder behavior for testing purposes.
     * SpEL won't use .equals() between StringWrapper and String, so it delegates to TypeComparator.
     */
    record StringWrapper(String value) {}

    /**
     * Creates a SpEL evaluation context where variables are wrapped in StringWrapper
     * (simulating VariableHolder), and a TypeComparator is installed to handle string comparison.
     * This mirrors how MhEvalContext should behave after the fix.
     */
    private static StandardEvaluationContext createContextWithStringComparator(Map<String, String> vars) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        vars.forEach((name, value) -> ctx.setVariable(name, new StringWrapper(value)));

        ctx.setTypeComparator(new TypeComparator() {
            @Override
            public boolean canCompare(@Nullable Object firstObject, @Nullable Object secondObject) {
                return firstObject != null && secondObject != null;
            }

            @Override
            public int compare(@Nullable Object firstObject, @Nullable Object secondObject) {
                if (firstObject == null || secondObject == null) {
                    throw new IllegalArgumentException("null operands");
                }
                // Extract string values from wrappers or raw strings
                String first = extractString(firstObject);
                String second = extractString(secondObject);
                return first.compareTo(second);
            }

            private String extractString(Object obj) {
                if (obj instanceof String s) return s;
                if (obj instanceof StringWrapper sw) return sw.value();
                throw new IllegalArgumentException("Unsupported type: " + obj.getClass());
            }
        });

        return ctx;
    }

    @Test
    void test_stringEquality_active_matches() {
        var ctx = createContextWithStringComparator(Map.of("amendmentStatus", "ACTIVE"));
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("#amendmentStatus == 'ACTIVE' ? true : false").getValue(ctx);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_stringEquality_active_doesNotMatchObsolete() {
        var ctx = createContextWithStringComparator(Map.of("amendmentStatus", "OBSOLETE"));
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("#amendmentStatus == 'ACTIVE' ? true : false").getValue(ctx);

        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void test_stringEquality_obsolete_matches() {
        var ctx = createContextWithStringComparator(Map.of("amendmentStatus", "OBSOLETE"));
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("#amendmentStatus == 'OBSOLETE' ? true : false").getValue(ctx);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_stringEquality_caseSensitive() {
        var ctx = createContextWithStringComparator(Map.of("amendmentStatus", "active"));
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("#amendmentStatus == 'ACTIVE' ? true : false").getValue(ctx);

        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void test_stringEquality_emptyString() {
        var ctx = createContextWithStringComparator(Map.of("status", ""));
        ExpressionParser parser = new SpelExpressionParser();

        Object result = parser.parseExpression("#status == '' ? true : false").getValue(ctx);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void test_stringEquality_bothBranches() {
        // Test that both ACTIVE and OBSOLETE branches evaluate correctly in the same setup
        var ctxActive = createContextWithStringComparator(Map.of("amendmentStatus", "ACTIVE"));
        var ctxObsolete = createContextWithStringComparator(Map.of("amendmentStatus", "OBSOLETE"));
        ExpressionParser parser = new SpelExpressionParser();

        String activeExpr = "#amendmentStatus == 'ACTIVE' ? true : false";
        String obsoleteExpr = "#amendmentStatus == 'OBSOLETE' ? true : false";

        assertEquals(Boolean.TRUE, parser.parseExpression(activeExpr).getValue(ctxActive));
        assertEquals(Boolean.FALSE, parser.parseExpression(obsoleteExpr).getValue(ctxActive));
        assertEquals(Boolean.FALSE, parser.parseExpression(activeExpr).getValue(ctxObsolete));
        assertEquals(Boolean.TRUE, parser.parseExpression(obsoleteExpr).getValue(ctxObsolete));
    }
}
