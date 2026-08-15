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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reading and applying the {@code analyzers} block of a Function descriptor.
 *
 * <p>Pure functions with no state and no context, so the rules that decide how long a failure
 * withholds work — and which rule wins when two are declared — can be tested directly.
 *
 * <p>Error code prefix: {@code 01.323.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Slf4j
public class FunctionAnalyzerUtils {

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile("^(\\d+)(ms|s|min|h|d)$");

    /** Scopes only the dispatcher may set. A Function descriptor declaring one of these is rejected. */
    private static final Set<String> DISPATCHER_ONLY_SCOPES = Set.of("global", "company");

    /** Scopes a Function descriptor may legitimately declare. */
    private static final Set<String> DESCRIPTOR_SCOPES = Set.of("api", "function", "processor");

    /**
     * Reads a declared timeout: {@code 500ms}, {@code 30s}, {@code 20min}, {@code 1h}, {@code 1d}.
     *
     * <p>❗ Throws rather than defaulting. A timeout that could not be read is a typo in a descriptor,
     * and quietly substituting some other duration would withhold work for a length of time nobody
     * chose — for longer than intended just as easily as for shorter.
     */
    public static Duration parseTimeout(String s) {
        if (S.b(s)) {
            throw new IllegalStateException("01.323.020 timeout is blank");
        }
        final Matcher m = TIMEOUT_PATTERN.matcher(s.strip());
        if (!m.matches()) {
            throw new IllegalStateException("01.323.040 can't parse timeout '" + s + "', expected digits followed by ms, s, min, h or d");
        }
        final long value = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "min" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> throw new IllegalStateException("01.323.060 unreachable unit: " + m.group(2));
        };
    }

    /**
     * The first analyzer whose any pattern appears anywhere in the output, or null when none does.
     *
     * <p>Matching is {@code find()}, not {@code matches()}: the interesting line is somewhere inside a
     * long console dump, never the whole of it. Patterns compile with NO implicit flags, so an author
     * wanting case-insensitivity writes {@code (?i)} — a hidden flag would silently widen every
     * pattern anyone had already written.
     *
     * <p>A pattern that does not compile is logged and skipped rather than thrown: one malformed rule
     * in a descriptor must not stop the others from being consulted, and this runs while handling a
     * failure that has already happened.
     */
    public static FunctionConfigYaml.@Nullable Analyzer firstHit(
            @Nullable List<FunctionConfigYaml.Analyzer> analyzers, @Nullable String console) {

        if (analyzers == null || analyzers.isEmpty() || S.b(console)) {
            return null;
        }
        for (FunctionConfigYaml.Analyzer analyzer : analyzers) {
            if (analyzer.regex == null || analyzer.regex.isEmpty()) {
                continue;
            }
            for (String regex : analyzer.regex) {
                if (S.b(regex)) {
                    continue;
                }
                try {
                    if (Pattern.compile(regex).matcher(console).find()) {
                        return analyzer;
                    }
                }
                catch (PatternSyntaxException e) {
                    log.error("01.323.080 analyzer '{}' declares a regex which doesn't compile: {}", analyzer.name, regex);
                }
            }
        }
        return null;
    }

    /**
     * Every console this Task produced, in the order they are consulted: the Function's own output
     * first, then the general one, then pre-functions, then post-functions.
     *
     * <p>❗ Read from the PARSED structure, never by matching the serialised column. Regexing the
     * stored text would match escaped characters and yaml keys as readily as real output, and it could
     * not tell a pre-function's failure from the main Function's — which is exactly the distinction
     * that decides what gets blocked.
     */
    public static List<String> consolesInOrder(FunctionApiData.@Nullable FunctionExec functionExec) {
        if (functionExec == null) {
            return List.of();
        }
        final List<String> consoles = new ArrayList<>();
        addConsole(consoles, functionExec.exec);
        addConsole(consoles, functionExec.generalExec);
        if (functionExec.preExecs != null) {
            for (FunctionApiData.SystemExecResult preExec : functionExec.preExecs) {
                addConsole(consoles, preExec);
            }
        }
        if (functionExec.postExecs != null) {
            for (FunctionApiData.SystemExecResult postExec : functionExec.postExecs) {
                addConsole(consoles, postExec);
            }
        }
        return consoles;
    }

    private static void addConsole(List<String> consoles, FunctionApiData.@Nullable SystemExecResult result) {
        if (result != null && !S.b(result.console)) {
            consoles.add(result.console);
        }
    }

    /**
     * The first analyzer that matches anything this Task printed. First hit wins, and the consoles are
     * walked in {@link #consolesInOrder} order, so the outcome does not depend on which of several
     * failing steps happens to be examined first.
     */
    public static FunctionConfigYaml.@Nullable Analyzer firstHitInExecResults(
            @Nullable List<FunctionConfigYaml.Analyzer> analyzers, FunctionApiData.@Nullable FunctionExec functionExec) {

        for (String console : consolesInOrder(functionExec)) {
            final FunctionConfigYaml.Analyzer hit = firstHit(analyzers, console);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Combines the two places analyzers can be declared. Both apply; where the same {@code name} is
     * declared in both, the LONGER timeout wins.
     *
     * <p>Longest-wins rather than function-overrides-dispatcher because these rules withhold work, and
     * the safer mistake is to withhold it for too long. A Function author shortening a limit the
     * installation set would otherwise be able to opt out of it entirely.
     */
    public static List<FunctionConfigYaml.Analyzer> merge(
            List<FunctionConfigYaml.Analyzer> dispatcherLevel, List<FunctionConfigYaml.Analyzer> functionLevel) {

        final Map<String, FunctionConfigYaml.Analyzer> byName = new LinkedHashMap<>();
        for (FunctionConfigYaml.Analyzer analyzer : dispatcherLevel) {
            byName.put(analyzer.name, analyzer);
        }
        for (FunctionConfigYaml.Analyzer analyzer : functionLevel) {
            byName.merge(analyzer.name, analyzer, FunctionAnalyzerUtils::longerTimeoutOf);
        }
        return new ArrayList<>(byName.values());
    }

    private static FunctionConfigYaml.Analyzer longerTimeoutOf(FunctionConfigYaml.Analyzer a, FunctionConfigYaml.Analyzer b) {
        return parseTimeout(b.timeout).compareTo(parseTimeout(a.timeout)) > 0 ? b : a;
    }

    /**
     * Rejects a descriptor-declared analyzer whose scope only the dispatcher may set.
     *
     * <p>❗ Throws at load time rather than downgrading to something legal. A silent downgrade would
     * leave the author believing a rule is in force when it is not, and the failure would only show up
     * as work that was never withheld.
     */
    public static void checkScopeAllowedInDescriptor(FunctionConfigYaml.Analyzer analyzer) {
        if (S.b(analyzer.scope)) {
            throw new IllegalStateException("01.323.100 analyzer '" + analyzer.name + "' declares no scope");
        }
        final String scope = analyzer.scope.strip();
        if (DISPATCHER_ONLY_SCOPES.contains(scope)) {
            throw new IllegalStateException(
                    "01.323.120 analyzer '" + analyzer.name + "' declares scope '" + scope + "', which only the dispatcher may set");
        }
        if (!DESCRIPTOR_SCOPES.contains(scope)) {
            throw new IllegalStateException(
                    "01.323.140 analyzer '" + analyzer.name + "' declares an unknown scope '" + scope + "'");
        }
    }
}
