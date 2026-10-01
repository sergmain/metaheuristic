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
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

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

    /**
     * Whether every dispatcher-sourced output of a Task has reached the Dispatcher.
     *
     * <p>An output counts as delivered when its Task-params {@code uploaded} flag is set, OR when its Variable is
     * already inited on the Dispatcher. The second condition is the contract the rest of the system already uses:
     * the upload endpoint answers OK for a Variable that is already inited, and the Processor, told by
     * {@code /variable-status} that a Variable is inited, marks the output delivered and never uploads it again.
     * In that case nothing on the Dispatcher ever sets the flag, so a check on the flag alone leaves the Task
     * IN_PROGRESS forever with its result already received.
     *
     * @param outputs the Task's output variables
     * @param isVariableInited whether the Variable with the given id is inited on the Dispatcher
     */
    public static boolean allOutputsUploaded(List<TaskParamsYaml.OutputVariable> outputs, Predicate<Long> isVariableInited) {
        return outputs.isEmpty() || outputs.stream()
                .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
                .allMatch(o->o.uploaded || isVariableInited.test(o.id));
    }
}
