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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import org.jspecify.annotations.Nullable;

/**
 * Pure helpers for the task-finishing path, extracted from {@link TaskFinishingTxService} so the
 * error-reporting logic can be unit-tested without a Spring context.
 *
 * @author Sergio Lissner
 */
public class TaskFinishingUtils {

    /**
     * Exit code recorded for a dispatcher-side system error — a task that failed before (or without)
     * producing a real processor console (e.g. a variable-init immutability violation).
     */
    public static final int SYSTEM_ERROR_EXIT_CODE = -10001;

    /**
     * Builds the {@link FunctionApiData.FunctionExec} to store when a task is finished in an error
     * state. A dispatcher-side failure has no processor console, so the error text is carried in
     * {@code console} and must be recorded as the task's exec with a non-zero exit code.
     */
    public static FunctionApiData.FunctionExec buildErrorFunctionExec(
            String functionCode, @Nullable String console, EnumsApi.TaskExecState targetState) {
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
        // FunctionExec.exec is field-initialized to a non-null default SystemExecResult, so it must be
        // OVERWRITTEN unconditionally — a null-check guard here would never fire and would silently keep
        // the placeholder (exitCode 0, no console), losing the error. Both error target states record
        // the system error identically.
        functionExec.exec = new FunctionApiData.SystemExecResult(
                functionCode, false, SYSTEM_ERROR_EXIT_CODE, console == null ? "<no console output>" : console);
        return functionExec;
    }
}
