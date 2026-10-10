/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mcp;

import ai.metaheuristic.api.EnumsApi;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Long-poll of an ExecContext's state, behind the MCP tool {@code mh_wait_exec_context}.
 *
 * <p>Why it exists: an MCP client that polls {@code mh_get_exec_context_info} pays a full model turn per poll,
 * whatever the answer. Waiting here costs the Dispatcher one cheap probe per second - the ExecContext's state plus
 * {@code ExecContextSegmentReadService.changeVersion}, two aggregate queries per table, no segment loaded - and costs
 * the client one call per wait window.
 *
 * <p>Pure loop over injected functions: the probe, the Task states, the clock and the sleeper. Production passes the
 * ExecContext cache, the segment read service, {@link System#currentTimeMillis} and a sleeping lambda, so the wait
 * logic itself runs without Spring.
 *
 * <p>Every wait ends on the first of: the condition is met, the ExecContext reached a terminal state (nothing more
 * will change, whatever was asked), or the time ran out.
 *
 * @author Serge
 * Date: 10/10/2026
 */
public final class ExecContextWaitUtils {

    private ExecContextWaitUtils() {}

    /** What the caller waits for. Every one of them also ends on a terminal ExecContext. */
    public enum Until {
        /** The ExecContext is FINISHED, ERROR, STOPPED or DOESNT_EXIST - the same set mh_get_exec_context_info names. */
        TERMINAL,
        /** At least one Task is in ERROR. ERROR_WITH_RECOVERY doesn't count - such a Task is going to be re-run. */
        ANY_ERROR,
        /** The version differs from {@code sinceVersion}; without one it is met at once, returning the current version. */
        STATE_CHANGED
    }

    public static final int MAX_ERROR_TASK_IDS = 5;

    /** The set {@code mh_get_exec_context_info} tells its callers to stop polling on. */
    private static final Set<EnumsApi.ExecContextState> TERMINAL_STATES = EnumSet.of(
            EnumsApi.ExecContextState.FINISHED, EnumsApi.ExecContextState.ERROR,
            EnumsApi.ExecContextState.STOPPED, EnumsApi.ExecContextState.DOESNT_EXIST);

    /** The probe of an ExecContext that is gone: terminal, so no wait outlives the ExecContext it waits on. */
    private static final Probe GONE = new Probe(EnumsApi.ExecContextState.DOESNT_EXIST, "");

    /**
     * The cheap part of one observation - nothing in it requires loading a segment.
     *
     * @param changeVersion the text {@code ExecContextSegmentReadService.changeVersion} returns
     */
    public record Probe(EnumsApi.ExecContextState state, String changeVersion) {
        /**
         * The cursor handed to the caller. The ExecContext's own state is part of it because a state change - STARTED
         * to FINISHED - writes no segment, so changeVersion alone would not see it.
         */
        public String version() {
            return state.code + ":" + changeVersion;
        }
    }

    /**
     * @param met              the condition asked for holds
     * @param terminal         the ExecContext is in a terminal state - nothing further will change
     * @param timedOut         neither of the above; the wait window ran out
     * @param version          pass it back as {@code sinceVersion}. It was read before the Task states below, so a change
     *                         landing in between is reported again by the next call rather than lost
     * @param taskStateCounts  non-zero counts only, by state name
     * @param firstErrorTaskIds Task ids in ERROR, ascending, at most {@link #MAX_ERROR_TASK_IDS}
     */
    public record WaitResult(
            boolean met,
            boolean terminal,
            boolean timedOut,
            String version,
            String stateName,
            long waitedMillis,
            Map<String, Integer> taskStateCounts,
            List<Long> firstErrorTaskIds
    ) {}

    public static boolean isTerminal(EnumsApi.ExecContextState state) {
        return TERMINAL_STATES.contains(state);
    }

    public static boolean isMet(Until until, Probe probe, @Nullable String sinceVersion, Map<Long, EnumsApi.TaskExecState> states) {
        return switch (until) {
            case TERMINAL -> isTerminal(probe.state());
            case ANY_ERROR -> states.containsValue(EnumsApi.TaskExecState.ERROR);
            case STATE_CHANGED -> sinceVersion == null || !sinceVersion.equals(probe.version());
        };
    }

    /**
     * Waits until {@code until} is met, the ExecContext is terminal, or {@code maxMillis} has passed.
     *
     * <p>The Task states are the expensive read - every segment of the ExecContext - so they are loaded only when
     * something changed since they were last loaded: on every probe whose version differs for {@link Until#ANY_ERROR},
     * and once at the end for the summary otherwise. The cost of a long wait on an idle ExecContext is the probes.
     *
     * @param probe      the ExecContext's state and change version; null when the ExecContext no longer exists
     * @param taskStates every Task's state, by Task id
     * @param clock      milliseconds
     * @param sleeper    sleeps the given milliseconds
     */
    public static WaitResult await(
            Until until, @Nullable String sinceVersion, long maxMillis, long pollMillis,
            Supplier<@Nullable Probe> probe,
            Supplier<Map<Long, EnumsApi.TaskExecState>> taskStates,
            LongSupplier clock,
            LongConsumer sleeper) {

        final long start = clock.getAsLong();
        String statesVersion = null;
        Map<Long, EnumsApi.TaskExecState> states = Map.of();
        while (true) {
            final Probe p = Objects.requireNonNullElse(probe.get(), GONE);
            if (until == Until.ANY_ERROR && !p.version().equals(statesVersion)) {
                states = taskStates.get();
                statesVersion = p.version();
            }
            final boolean met = isMet(until, p, sinceVersion, states);
            final boolean terminal = isTerminal(p.state());
            final long waited = clock.getAsLong() - start;
            if (met || terminal || waited >= maxMillis) {
                if (!p.version().equals(statesVersion)) {
                    states = taskStates.get();
                }
                return toResult(met, terminal, !met && !terminal, p, waited, states);
            }
            sleeper.accept(Math.min(pollMillis, maxMillis - waited));
        }
    }

    static WaitResult toResult(boolean met, boolean terminal, boolean timedOut, Probe probe, long waitedMillis,
                               Map<Long, EnumsApi.TaskExecState> states) {
        final Map<String, Integer> counts = new TreeMap<>();
        states.values().forEach(s -> counts.merge(s.name(), 1, Integer::sum));
        final List<Long> errorTaskIds = states.entrySet().stream()
                .filter(e -> e.getValue() == EnumsApi.TaskExecState.ERROR)
                .map(Map.Entry::getKey)
                .sorted()
                .limit(MAX_ERROR_TASK_IDS)
                .toList();
        return new WaitResult(met, terminal, timedOut, probe.version(), probe.state().name(), waitedMillis, counts, errorTaskIds);
    }
}
