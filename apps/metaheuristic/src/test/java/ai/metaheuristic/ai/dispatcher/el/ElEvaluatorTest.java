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
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 3/2/2026
 */
@Execution(CONCURRENT)
class ElEvaluatorTest {

    /**
     * Creates a SpEL-backed evaluator that resolves expressions against a simple variable map,
     * simulating MH variable lookup for testing purposes.
     */
    private static Function<String, Object> spelEvaluator(Map<String, Object> vars) {
        SpelExpressionParser parser = new SpelExpressionParser();
        return expression -> {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            vars.forEach(ctx::setVariable);
            // SpEL references variables with #, so prefix each var reference
            String spelExpr = expression;
            for (String varName : vars.keySet()) {
                spelExpr = spelExpr.replace(varName, "#" + varName);
            }
            Object result = parser.parseExpression(spelExpr).getValue(ctx);
            return result;
        };
    }

    @Test
    void test_resolve_simpleVariable() {
        //act
        String result = ElEvaluator.resolve("parentId{{level}}", spelEvaluator(Map.of("level", 1)));

        assertEquals("parentId1", result);
    }

    @Test
    void test_resolve_zeroLevel() {
        //act
        String result = ElEvaluator.resolve("reqJson{{level}}", spelEvaluator(Map.of("level", 0)));

        assertEquals("reqJson0", result);
    }

    @Test
    void test_resolve_arithmetic() {
        //act
        String result = ElEvaluator.resolve("check-objectives-{{level + 1}}", spelEvaluator(Map.of("level", 0)));

        assertEquals("check-objectives-1", result);
    }

    @Test
    void test_resolve_noPlaceholders() {
        //act
        String result = ElEvaluator.resolve("no-placeholders", spelEvaluator(Map.of("level", 1)));

        assertEquals("no-placeholders", result);
    }

    @Test
    void test_resolve_nullVars() {
        //act
        String result = ElEvaluator.resolve("parentId{{level}}", null);

        assertEquals("parentId{{level}}", result);
    }

    @Test
    void test_resolve_multiplePlaceholders() {
        //act
        String result = ElEvaluator.resolve("{{a}}{{b}}", spelEvaluator(Map.of("a", "hello", "b", "World")));

        assertEquals("helloWorld", result);
    }

    @Test
    void test_resolveMetas_basic() {
        List<Map<String, String>> metas = new ArrayList<>();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("variable-for-parentId", "parentId{{level}}");
        metas.add(meta);

        //act
        List<Map<String, String>> result = ElEvaluator.resolveMetas(metas, spelEvaluator(Map.of("level", 2)));

        assertEquals(1, result.size());
        assertEquals("parentId2", result.getFirst().get("variable-for-parentId"));
    }

    @Test
    void test_resolveMetas_nullVars_returnsSameList() {
        List<Map<String, String>> metas = List.of(Map.of("key", "value"));

        //act
        List<Map<String, String>> result = ElEvaluator.resolveMetas(metas, null);

        assertSame(metas, result);
    }

    @Test
    void test_resolve_topLevelReqs() {
        //act
        String result = ElEvaluator.resolve("topLevelReqs{{level}}", spelEvaluator(Map.of("level", 3)));

        assertEquals("topLevelReqs3", result);
    }

    @Test
    void test_resolve_arithmeticSubtraction() {
        //act
        String result = ElEvaluator.resolve("level{{level - 1}}", spelEvaluator(Map.of("level", 3)));

        assertEquals("level2", result);
    }
}
