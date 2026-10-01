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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Characterization test for {@link TaskFinishingUtils#buildErrorFunctionExec} — the exec that a
 * task finished in an error state carries.
 *
 * Task #1387 (execContext #32) failed dispatcher-side with a variable immutability violation and
 * was stored with {@code exec: {exitCode: 0, isOk: false}} — a default placeholder, NOT the actual
 * error. Root cause: {@code FunctionExec.exec} is field-initialized to a non-null default
 * {@code SystemExecResult}, so the {@code ERROR}-branch guard {@code if (functionExec.exec==null)}
 * is always false and the real error is never recorded.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class TaskFinishingUtilsTest {

    /**
     * Red -> Green-3: for a targetState of ERROR, the error must be recorded — the actual console
     * text and a non-zero exit code — not the default placeholder.
     */
    @Test
    public void test_buildErrorFunctionExec_error_recordsError() {
        String console = "179.300 Variable immutability violation for task #1387: 171.863 Variable 'requirementId' already exists in outer context '1,2#1' and cannot be redeclared in context '1,2,3,6,7,8,9,13|1|0|0|0|0|0#1'. Variables are immutable by default.";

        FunctionApiData.FunctionExec fe = TaskFinishingUtils.buildErrorFunctionExec(
                "mhdg-rg.store-req", console, EnumsApi.TaskExecState.ERROR);

        assertNotNull(fe.exec);
        assertEquals(TaskFinishingUtils.SYSTEM_ERROR_EXIT_CODE, fe.exec.exitCode);
        assertEquals(console, fe.exec.console);
        assertEquals("mhdg-rg.store-req", fe.exec.functionCode);
        assertFalse(fe.exec.isOk);
    }

    /**
     * Guard: the ERROR_WITH_RECOVERY path already records the error correctly and must not regress.
     */
    @Test
    public void test_buildErrorFunctionExec_errorWithRecovery_recordsError() {
        FunctionApiData.FunctionExec fe = TaskFinishingUtils.buildErrorFunctionExec(
                "fn", "boom", EnumsApi.TaskExecState.ERROR_WITH_RECOVERY);

        assertNotNull(fe.exec);
        assertEquals(TaskFinishingUtils.SYSTEM_ERROR_EXIT_CODE, fe.exec.exitCode);
        assertEquals("boom", fe.exec.console);
        assertEquals("fn", fe.exec.functionCode);
        assertFalse(fe.exec.isOk);
    }

    private static TaskParamsYaml.OutputVariable output(Long id, EnumsApi.DataSourcing sourcing, boolean uploaded) {
        final TaskParamsYaml.OutputVariable o = new TaskParamsYaml.OutputVariable();
        o.id = id;
        o.context = EnumsApi.VariableContext.local;
        o.name = "var-" + id;
        o.sourcing = sourcing;
        o.uploaded = uploaded;
        return o;
    }

    // allOutputsUploaded(): the inited ids are a real in-memory store; production passes a Dispatcher-side Variable lookup
    // through the same parameter

    @Test
    public void test_allOutputsUploaded_noOutputs() {
        assertTrue(TaskFinishingUtils.allOutputsUploaded(List.of(), Set.<Long>of()::contains));
    }

    @Test
    public void test_allOutputsUploaded_flaggedUploaded_variableNotInited() {
        assertTrue(TaskFinishingUtils.allOutputsUploaded(
                List.of(output(11L, EnumsApi.DataSourcing.dispatcher, true)), Set.<Long>of()::contains));
    }

    /** Task #7460: the output reached the Dispatcher (Variable inited) but the uploaded flag was never set. */
    @Test
    public void test_allOutputsUploaded_notFlagged_variableInited() {
        assertTrue(TaskFinishingUtils.allOutputsUploaded(
                List.of(output(5997L, EnumsApi.DataSourcing.dispatcher, false)), Set.of(5997L)::contains));
    }

    @Test
    public void test_allOutputsUploaded_notFlagged_variableNotInited() {
        assertFalse(TaskFinishingUtils.allOutputsUploaded(
                List.of(output(21L, EnumsApi.DataSourcing.dispatcher, false)), Set.<Long>of()::contains));
    }

    @Test
    public void test_allOutputsUploaded_oneOfTwoMissing() {
        assertFalse(TaskFinishingUtils.allOutputsUploaded(
                List.of(output(31L, EnumsApi.DataSourcing.dispatcher, false), output(32L, EnumsApi.DataSourcing.dispatcher, false)),
                Set.of(31L)::contains));
    }

    @Test
    public void test_allOutputsUploaded_nonDispatcherOutputIgnored() {
        assertTrue(TaskFinishingUtils.allOutputsUploaded(
                List.of(output(41L, EnumsApi.DataSourcing.disk, false), output(42L, EnumsApi.DataSourcing.dispatcher, true)),
                Set.<Long>of()::contains));
    }
}
