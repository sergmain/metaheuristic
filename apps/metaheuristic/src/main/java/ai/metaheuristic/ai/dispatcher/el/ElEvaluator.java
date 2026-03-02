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
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{expr}} placeholders in meta values using SpEL evaluation
 * against MH variable context via EvaluateExpressionLanguage.
 *
 * @author Serge
 * Date: 3/2/2026
 */
@Slf4j
public class ElEvaluator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    /**
     * Resolves all {{expr}} placeholders in the given template string by evaluating
     * each expression via the provided evaluator function.
     *
     * @param template the template string with {{expr}} placeholders
     * @param evaluator function that takes a SpEL expression and returns its evaluated result
     * @return resolved string, or original template if no placeholders found
     */
    public static String resolve(String template, @Nullable Function<String, Object> evaluator) {
        if (template == null || template.isEmpty() || evaluator == null) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        if (!matcher.find()) {
            return template;
        }

        StringBuilder sb = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            String expression = matcher.group(1).strip();
            try {
                Object result = evaluator.apply(expression);
                String replacement = result != null ? result.toString() : "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            catch (Exception e) {
                log.warn("376.020 Failed to evaluate expression '{}' in template '{}': {}", expression, template, e.getMessage());
                // leave placeholder as-is on failure
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves all {{expr}} placeholders in all meta values using the provided evaluator.
     *
     * @return new list with resolved values, or the same list if no resolution needed
     */
    public static List<Map<String, String>> resolveMetas(List<Map<String, String>> metas, @Nullable Function<String, Object> evaluator) {
        if (metas == null || metas.isEmpty() || evaluator == null) {
            return metas;
        }

        boolean anyChanged = false;
        List<Map<String, String>> result = new ArrayList<>(metas.size());

        for (Map<String, String> meta : metas) {
            Map<String, String> resolvedMeta = new LinkedHashMap<>();
            boolean metaChanged = false;
            for (Map.Entry<String, String> entry : meta.entrySet()) {
                String value = entry.getValue();
                String resolved = resolve(value, evaluator);
                resolvedMeta.put(entry.getKey(), resolved);
                if (!resolved.equals(value)) {
                    metaChanged = true;
                }
            }
            result.add(metaChanged ? resolvedMeta : meta);
            if (metaChanged) {
                anyChanged = true;
            }
        }

        return anyChanged ? result : metas;
    }

    /**
     * Creates an evaluator function backed by EvaluateExpressionLanguage that resolves
     * expressions against MH variables.
     */
    public static Function<String, Object> createMhEvaluator(
            Long execContextId, Long taskId, String taskContextId,
            InternalFunctionVariableService internalFunctionVariableService,
            GlobalVariableTxService globalVariableService,
            VariableTxService variableTxService,
            VariableRepository variableRepository,
            Consumer<Variable> setAsNullFunction) {
        return expression -> {
            Object result = EvaluateExpressionLanguage.evaluate(
                    execContextId, taskId, taskContextId, expression,
                    internalFunctionVariableService, globalVariableService, variableTxService,
                    variableRepository, setAsNullFunction);
            return result;
        };
    }
}
