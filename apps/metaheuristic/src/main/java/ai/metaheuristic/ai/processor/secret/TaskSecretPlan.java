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

package ai.metaheuristic.ai.processor.secret;

import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Task-level secret-handoff planner. Walks the pre + main + post Function
 * configs in a task, finds which one (if any) declares an API key, and
 * returns a {@link Plan} describing what the launcher should do.
 *
 * <p>INV-5 (one secret per task params): if more than one Function in the
 * task declares an API key, that's a contract violation. The plan reports
 * it; the caller marks the task with an error rather than attempting a
 * multi-secret handoff (multi-secret tasks must be split into separate
 * DAG steps at the SourceCode level).
 *
 * <p>Pure static function: dependencies injected as lambdas. No Spring, no
 * mocks needed for testing.
 *
 * <p>Phase indices:
 * <ul>
 *   <li>{@code phase == "pre[N]"} for N-th pre-Function (N from 0).</li>
 *   <li>{@code phase == "main"}   for the main Function.</li>
 *   <li>{@code phase == "post[N]"} for N-th post-Function.</li>
 * </ul>
 *
 * @author Sergio Lissner
 */
public final class TaskSecretPlan {

    private TaskSecretPlan() {}

    public enum Kind { NO_SECRET_NEEDED, AWAITING, READY, MULTI_SECRET_VIOLATION }

    /**
     * Result of planning. Exactly one variant is "live" depending on
     * {@link #kind}:
     * <ul>
     *   <li>{@code NO_SECRET_NEEDED}: nothing further to do.</li>
     *   <li>{@code AWAITING}: cache miss; caller enqueues a fetch for
     *       {@code keyCode} and skips this task cycle.</li>
     *   <li>{@code READY}: exactly one phase needs a secret and the cache
     *       has the sealed bytes. Caller decrypts {@code sealed}, generates
     *       a fresh check-code, opens a channel, writes the params file
     *       with check-code + port, launches.</li>
     *   <li>{@code MULTI_SECRET_VIOLATION}: caller marks the task with
     *       an error using {@code violationMessage}.</li>
     * </ul>
     */
    public record Plan(
            Kind kind,
            @Nullable String phase,
            @Nullable String keyCode,
            @Nullable SealedSecret sealed,
            @Nullable String violationMessage) {

        public static Plan noSecretNeeded() {
            return new Plan(Kind.NO_SECRET_NEEDED, null, null, null, null);
        }

        public static Plan awaiting(String keyCode) {
            return new Plan(Kind.AWAITING, null, keyCode, null, null);
        }

        public static Plan ready(String phase, String keyCode, SealedSecret sealed) {
            return new Plan(Kind.READY, phase, keyCode, sealed, null);
        }

        public static Plan violation(String message) {
            return new Plan(Kind.MULTI_SECRET_VIOLATION, null, null, null, message);
        }
    }

    /**
     * @param task         the task's TaskYaml — must have non-null function list
     * @param companyId    task's companyId; 0L is the sentinel for "no company,
     *                     no secrets" (skip the entire plan, return NO_SECRET_NEEDED)
     * @param cacheLookup  given a keyCode, returns the cached SealedSecret or
     *                     null on cache miss
     */
    public static Plan plan(
            TaskParamsYaml.TaskYaml task,
            long companyId,
            Function<String, @Nullable SealedSecret> cacheLookup) {

        if (companyId == 0L) {
            return Plan.noSecretNeeded();
        }

        // Walk pre + main + post in stable order; collect (phase, api) pairs
        // for any Function that declares an api.
        record PhaseApi(String phase, String keyCode) {}
        List<PhaseApi> withApi = new ArrayList<>();

        if (task.preFunctions != null) {
            for (int i = 0; i < task.preFunctions.size(); i++) {
                TaskParamsYaml.FunctionConfig fc = task.preFunctions.get(i);
                String kc = activeKeyCode(fc);
                if (kc != null) {
                    withApi.add(new PhaseApi("pre[" + i + "]", kc));
                }
            }
        }
        if (task.function != null) {
            String kc = activeKeyCode(task.function);
            if (kc != null) {
                withApi.add(new PhaseApi("main", kc));
            }
        }
        if (task.postFunctions != null) {
            for (int i = 0; i < task.postFunctions.size(); i++) {
                TaskParamsYaml.FunctionConfig fc = task.postFunctions.get(i);
                String kc = activeKeyCode(fc);
                if (kc != null) {
                    withApi.add(new PhaseApi("post[" + i + "]", kc));
                }
            }
        }

        if (withApi.isEmpty()) {
            return Plan.noSecretNeeded();
        }

        if (withApi.size() > 1) {
            StringBuilder phases = new StringBuilder();
            for (int i = 0; i < withApi.size(); i++) {
                if (i > 0) phases.append(", ");
                phases.append(withApi.get(i).phase()).append("=").append(withApi.get(i).keyCode());
            }
            return Plan.violation(
                    "100.500 INV-5 violation: multi-secret task. " +
                    "Functions with api: " + phases + ". " +
                    "Multi-secret tasks must be split into separate DAG steps.");
        }

        PhaseApi only = withApi.get(0);
        SealedSecret sealed = cacheLookup.apply(only.keyCode());
        if (sealed == null) {
            return Plan.awaiting(only.keyCode());
        }
        return Plan.ready(only.phase(), only.keyCode(), sealed);
    }

    /** Returns the keyCode if present and non-blank; otherwise null. */
    private static @Nullable String activeKeyCode(TaskParamsYaml.@Nullable FunctionConfig fc) {
        if (fc == null) return null;
        if (fc.api == null) return null;
        String kc = fc.api.keyCode;
        if (kc == null || kc.isBlank()) return null;
        return kc;
    }
}
