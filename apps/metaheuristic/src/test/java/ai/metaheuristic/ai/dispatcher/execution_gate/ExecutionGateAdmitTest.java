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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.GateData;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The admission decision table, without a Spring context.
 *
 * <p>One case per rejection reason plus the admitted path. Each case starts from a fixture that WOULD
 * be admitted and breaks exactly one thing, so a passing test says "this reason, and only because of
 * this" rather than "something was wrong somewhere".
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Execution(CONCURRENT)
public class ExecutionGateAdmitTest {

    private static final Predicate<TaskParamsYaml.FunctionConfig> ALWAYS_TRUSTED = fc -> true;
    private static final Predicate<TaskParamsYaml.FunctionConfig> NEVER_TRUSTED = fc -> false;

    private static final int CURRENT_TASK_PARAMS_VERSION = 3;

    @Test
    public void test_admitted_whenNothingIsWrong() {
        final GateData.Admission admission =
                ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(taskParams()), false, ALWAYS_TRUSTED);

        assertTrue(admission.admitted());
        assertNull(admission.rejectedBy());
    }

    @Test
    public void test_gitRequired_whenTheFunctionNeedsGitAndTheProcessorHasNone() {
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.sourcing = EnumsApi.FunctionSourcing.git;

        final ProcessorStatusYaml psy = processorStatus();
        psy.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.not_found);

        assertRejectedBy(Enums.TaskRejectingStatus.git_required,
                ExecutionGateUtils.admitStatelessFacts(processor(psy, coreStatus()), queuedTask(tpy), false, ALWAYS_TRUSTED));
    }

    @Test
    public void test_gitInstalled_admitsAGitSourcedFunction() {
        // the mirror of the case above, so the rejection above is attributable to git and not to
        // sourcing per se
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.sourcing = EnumsApi.FunctionSourcing.git;

        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(tpy), false, ALWAYS_TRUSTED).admitted());
    }

    @Test
    public void test_tagsArentAllowed_whenTheTaskCarriesATagTheCoreDoesNot() {
        final CoreStatusYaml csy = coreStatus();
        csy.tags = "gpu,bigmem";

        assertRejectedBy(Enums.TaskRejectingStatus.tags_arent_allowed,
                ExecutionGateUtils.admitStatelessFacts(processor(processorStatus(), csy), queuedTask(taskParams(), "fpga"), false, ALWAYS_TRUSTED));
    }

    @Test
    public void test_tagsAllowed_whenTheCoreDeclaresTheTaskTag() {
        final CoreStatusYaml csy = coreStatus();
        csy.tags = "gpu,bigmem";

        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(processorStatus(), csy), queuedTask(taskParams(), "gpu"), false, ALWAYS_TRUSTED).admitted());
    }

    @Test
    public void test_interpreterIsUndefined_whenTheProcessorLacksTheDeclaredEnv() {
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.env = "env-nobody-declares";

        assertRejectedBy(Enums.TaskRejectingStatus.interpreter_is_undefined,
                ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(tpy), false, ALWAYS_TRUSTED));
    }

    @Test
    public void test_interpreterPresent_admits() {
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.env = "python-3";

        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(tpy), false, ALWAYS_TRUSTED).admitted());
    }

    @Test
    public void test_notSupportedOperatingSystem_whenTheFunctionNamesADifferentOne() {
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.metas.add(Map.of(ConstsApi.META_MH_FUNCTION_SUPPORTED_OS, EnumsApi.OS.windows.name()));

        final ProcessorStatusYaml psy = processorStatus();
        psy.os = EnumsApi.OS.linux;

        assertRejectedBy(Enums.TaskRejectingStatus.not_supported_operating_system,
                ExecutionGateUtils.admitStatelessFacts(processor(psy, coreStatus()), queuedTask(tpy), false, ALWAYS_TRUSTED));
    }

    @Test
    public void test_operatingSystemIgnored_whenTheFunctionNamesNone() {
        // an empty supported-OS list means "any", not "none"
        final ProcessorStatusYaml psy = processorStatus();
        psy.os = EnumsApi.OS.linux;

        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(psy, coreStatus()), queuedTask(taskParams()), false, ALWAYS_TRUSTED).admitted());
    }

    @Test
    public void test_acceptOnlySigned_whenTheFunctionIsUntrustedAndUnsigned() {
        assertRejectedBy(Enums.TaskRejectingStatus.accept_only_signed,
                ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(taskParams()), true, NEVER_TRUSTED));
    }

    @Test
    public void test_acceptOnlySigned_isNotAppliedWhenTheCoreDidNotAskForIt() {
        // the same untrusted, unsigned Function is fine when the Processor did not request the check
        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(taskParams()), false, NEVER_TRUSTED).admitted());
    }

    @Test
    public void test_acceptOnlySigned_isNotAppliedToATrustedFunction() {
        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(taskParams()), true, ALWAYS_TRUSTED).admitted());
    }

    @Test
    public void test_acceptOnlySigned_isSatisfiedByASignedChecksum() {
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.checksumMap = Map.of(EnumsApi.HashAlgo.SHA256WithSignature, "some-signature");

        assertTrue(ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(tpy), true, NEVER_TRUSTED).admitted());
    }

    @Test
    public void test_acceptOnlySigned_isNotSatisfiedByAnUnsignedChecksum() {
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.checksumMap = Map.of(EnumsApi.HashAlgo.SHA256, "some-hash");

        assertRejectedBy(Enums.TaskRejectingStatus.accept_only_signed,
                ExecutionGateUtils.admitStatelessFacts(processor(), queuedTask(tpy), true, NEVER_TRUSTED));
    }

    @Test
    public void test_downgradeToAnOlderTaskParamsVersion_throwsClassCastExceptionRatherThanRejecting() {
        // ❗ Characterization of a PRE-EXISTING defect, not of intended behaviour.
        // TaskParamsYamlUtilsV3 declares its downgrade type as Void, so the compiler generates a
        // bridge downgradeTo(Object) that casts to Void before the body runs. BaseYamlUtils calls it
        // through a raw reference with a TaskParamsYaml, so the cast fails and the
        // DowngradeNotSupportedException written inside the method is never reached.
        // Consequence: Enums.TaskRejectingStatus.downgrade_not_supported cannot be produced, here or
        // in the existing filter chain, whose identical catch is therefore dead code.
        // This method mirrors that chain deliberately, so the redirect onto it stays
        // behaviour-preserving; it inherits the defect until the defect is fixed.
        final ProcessorStatusYaml psy = processorStatus();
        psy.taskParamsVersion = 1;

        assertThrows(ClassCastException.class,
                () -> ExecutionGateUtils.admitParamsVersion(processor(psy, coreStatus()), queuedTask(taskParams())));
    }

    @Test
    public void test_noDowngradeAttempted_whenTheVersionsAlreadyMatch() {
        final ProcessorStatusYaml psy = processorStatus();
        psy.taskParamsVersion = CURRENT_TASK_PARAMS_VERSION;

        assertTrue(ExecutionGateUtils.admitParamsVersion(processor(psy, coreStatus()), queuedTask(taskParams())).admitted());
    }

    @Test
    public void test_theFirstFailingCheckWins_gitBeforeTags() {
        // order matters for diagnosability: the reported reason must be the same one the existing
        // filter chain would report, and that chain tests git before tags
        final TaskParamsYaml tpy = taskParams();
        tpy.task.function.sourcing = EnumsApi.FunctionSourcing.git;

        final ProcessorStatusYaml psy = processorStatus();
        psy.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.not_found);

        final CoreStatusYaml csy = coreStatus();
        csy.tags = "gpu";

        assertRejectedBy(Enums.TaskRejectingStatus.git_required,
                ExecutionGateUtils.admitStatelessFacts(processor(psy, csy), queuedTask(tpy, "fpga"), false, ALWAYS_TRUSTED));
    }

    @Test
    public void test_aNullTaskSkipsTheDowngradeCheckRatherThanThrowing() {
        // the raw params live on the Task row; the caller has already rejected a null one for its own
        // reason, so this method must not fall over if it sees one
        final ProcessorStatusYaml psy = processorStatus();
        psy.taskParamsVersion = 1;

        final TaskQueue.QueuedTask queuedTask = new TaskQueue.QueuedTask(
                EnumsApi.FunctionExecContext.external, 42L, 43L, null, taskParams(), null, 1);

        assertTrue(ExecutionGateUtils.admitParamsVersion(processor(psy, coreStatus()), queuedTask).admitted());
    }

    private static void assertRejectedBy(Enums.TaskRejectingStatus expected, GateData.Admission actual) {
        assertFalse(actual.admitted(), "expected a rejection with " + expected);
        assertEquals(expected, actual.rejectedBy());
    }

    private static ProcessorData.ProcessorAndCoreParams processor() {
        return processor(processorStatus(), coreStatus());
    }

    private static ProcessorData.ProcessorAndCoreParams processor(ProcessorStatusYaml psy, CoreStatusYaml csy) {
        return new ProcessorData.ProcessorAndCoreParams(101L, 201L, psy, csy);
    }

    private static ProcessorStatusYaml processorStatus() {
        final ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.env = new ProcessorStatusYaml.Env();
        psy.env.envs.put("python-3", "/usr/bin/python3");
        psy.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.installed);
        psy.taskParamsVersion = CURRENT_TASK_PARAMS_VERSION;
        psy.os = null;
        return psy;
    }

    private static CoreStatusYaml coreStatus() {
        final CoreStatusYaml csy = new CoreStatusYaml();
        csy.tags = null;
        return csy;
    }

    private static TaskQueue.QueuedTask queuedTask(TaskParamsYaml tpy) {
        return queuedTask(tpy, null);
    }

    private static TaskQueue.QueuedTask queuedTask(TaskParamsYaml tpy, String tag) {
        final TaskImpl task = new TaskImpl();
        task.id = 43L;
        task.execContextId = 42L;
        task.execState = EnumsApi.TaskExecState.NONE.value;
        task.setParams(TaskParamsYamlUtils.UTILS.toString(tpy));

        return new TaskQueue.QueuedTask(EnumsApi.FunctionExecContext.external, 42L, 43L, task, tpy, tag, 1);
    }

    private static TaskParamsYaml taskParams() {
        final TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = 42L;
        tpy.task.taskContextId = CommonConsts.TOP_LEVEL_CONTEXT_ID;
        tpy.task.processCode = "some-process";
        tpy.task.context = EnumsApi.FunctionExecContext.external;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = "function-01:1.1";
        return tpy;
    }
}
