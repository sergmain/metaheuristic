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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Who decides that a git-sourced Function is ready to run.
 *
 * <p>For a dispatcher-sourced Function the Dispatcher knows: the Processor downloads it through the
 * asset-manager protocol and reports the codes it holds, and {@code allFunctionsReady} consults that
 * report before a Task is handed over.
 *
 * <p>A git-sourced Function works the same way, with one difference: what is reported is the CODE AND
 * THE REVISION. HEAD cannot be advertised - it names no revision until an ExecContext resolves one, and
 * advertising it would invite the Processor to choose a revision of its own, which is what pinning
 * exists to prevent. The resolved sha can be, and is: {@code registerResolvedGitRevisions} records it at
 * ExecContext creation and the broadcast carries one entry per pinned sha.
 *
 * <p>❗ Keying that report by code alone would be wrong in both directions. It would let a Task pinned to
 * one sha be admitted on a Processor that only ever fetched another; and before the fix it deadlocked,
 * because a HEAD descriptor was never advertised at all, so no report ever arrived, so the Task was
 * withheld as {@code functions_not_ready} forever - while the per-Task path that would have prepared it
 * only runs on a Task the Processor was given.
 *
 * @author Serge
 * Date: 9/5/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecutionGateGitFunctionReadinessTest {

    private static ExecutionGateService gateService() {
        // allFunctionsReady reads only this instance's own readiness map, so the collaborators
        // it never reaches on this path are not supplied
        return new ExecutionGateService(null, null, null, null, null);
    }

    private static TaskParamsYaml task(String functionCode, EnumsApi.FunctionSourcing sourcing, String commit) {
        final TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task = new TaskParamsYaml.TaskYaml();
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = functionCode;
        tpy.task.function.sourcing = sourcing;
        if (sourcing==EnumsApi.FunctionSourcing.git) {
            final GitInfo git = new GitInfo();
            git.repo = "https://github.com/sergmain/metaheuristic-assets.git";
            git.branch = "master";
            git.commit = commit;
            git.path = "verify-git-cycle/payload/fn-hello-git";
            tpy.task.function.git = git;
        }
        return tpy;
    }

    private static final String SHA_A = "3536035345f33a8dd0751744b4877569f0566d20";
    private static final String SHA_B = "a7c0ad00d8318a6b709c06bd64a40afef52fe01b";

    /** A report naming the pinned revision admits the Task. */
    @Test
    public void test_gitSourcedFunctionIsAdmittedOnceTheRevisionIsReported() {
        final ExecutionGateService gate = gateService();
        gate.recordFunctionReadiness(ExecutionGateService.readinessKey("mh-verify.hello-git_1.1", SHA_A), 1L);

        assertTrue(gate.allFunctionsReady(1L, task("mh-verify.hello-git_1.1", EnumsApi.FunctionSourcing.git, SHA_A)));
    }

    /**
     * ❗ The reason the key carries the sha. Reporting one revision must not admit a Task pinned to
     * another, because the Processor has never fetched it.
     */
    @Test
    public void test_reportingOneRevisionDoesNotAdmitATaskPinnedToAnother() {
        final ExecutionGateService gate = gateService();
        gate.recordFunctionReadiness(ExecutionGateService.readinessKey("mh-verify.hello-git_1.1", SHA_A), 1L);

        assertFalse(gate.allFunctionsReady(1L, task("mh-verify.hello-git_1.1", EnumsApi.FunctionSourcing.git, SHA_B)),
                "SHA_B was never fetched by this Processor, so a report about SHA_A says nothing about it");
    }

    /** Nothing reported at all is still not ready - the fix advertises the revision, it doesn't skip the check. */
    @Test
    public void test_gitSourcedFunctionIsNotAdmittedBeforeAnyReport() {
        final ExecutionGateService gate = gateService();
        gate.seedFunctionReadiness(ExecutionGateService.readinessKey("mh-verify.hello-git_1.1", SHA_A));

        assertFalse(gate.allFunctionsReady(1L, task("mh-verify.hello-git_1.1", EnumsApi.FunctionSourcing.git, SHA_A)),
                "seeding starts the entry's lifetime; it does not claim any Processor holds the revision");
    }

    /**
     * ❗ The advertise/report round trip must terminate.
     *
     * <p>The Processor answers the broadcast with the keys it holds, and the Dispatcher's reply to THAT
     * must offer nothing further - {@code FunctionRepositoryRequestor} treats a non-empty second reply as
     * a protocol violation and throws {@code 778.050 isNotEmpty(p)}. So whatever key the Processor
     * reports has to be the same key the Dispatcher filters its advertisements by. Reporting
     * {@code code@sha} while filtering on {@code code} never matches, and the pair loops for ever.
     */
    @Test
    public void test_theKeyAProcessorReportsIsTheKeyThatSuppressesTheNextAdvertisement() {
        final ExecutionGateService gate = gateService();
        final String reportedByProcessor = ExecutionGateService.readinessKey("mh-verify.hello-git_1.1", SHA_A);

        gate.recordFunctionReadiness(reportedByProcessor, 1L);

        assertTrue(gate.isProcessorReady(ExecutionGateService.readinessKey("mh-verify.hello-git_1.1", SHA_A), 1L),
                "the Dispatcher must recognise the very key the Processor just reported, or it re-advertises "
                + "for ever and the protocol check fires on every poll");
    }

    /** And the code is recoverable from the key, which is how a report is matched to an active Function. */
    @Test
    public void test_functionCodeIsRecoverableFromAReadinessKey() {
        assertEquals("mh-verify.hello-git_1.1",
                ExecutionGateService.functionCodeOfReadinessKey(
                        ExecutionGateService.readinessKey("mh-verify.hello-git_1.1", SHA_A)));
        assertEquals("mh-verify.hello-dispatcher_1.1",
                ExecutionGateService.functionCodeOfReadinessKey("mh-verify.hello-dispatcher_1.1"));
    }

    /** The key itself, since both sides of the protocol have to build the same string. */
    @Test
    public void test_readinessKeyCarriesTheRevisionOnlyForGitSourcing() {
        assertEquals("mh-verify.hello-git_1.1@" + SHA_A,
                ExecutionGateService.readinessKey(task("mh-verify.hello-git_1.1", EnumsApi.FunctionSourcing.git, SHA_A).task.function));
        assertEquals("mh-verify.hello-dispatcher_1.1",
                ExecutionGateService.readinessKey(task("mh-verify.hello-dispatcher_1.1", EnumsApi.FunctionSourcing.dispatcher, null).task.function));
        assertEquals("code-with-no-commit", ExecutionGateService.readinessKey("code-with-no-commit", null));
    }

    /** ❗ The dispatcher-sourced contract is untouched: no report, no Task. */
    @Test
    public void test_dispatcherSourcedFunctionStillRequiresAProcessorReport() {
        final ExecutionGateService gate = gateService();
        final TaskParamsYaml tpy = task("mh-verify.hello-dispatcher_1.1", EnumsApi.FunctionSourcing.dispatcher, null);

        assertFalse(gate.allFunctionsReady(1L, tpy),
                "nothing reported this Function, so the Processor cannot be assumed to hold it");
    }

    /** And once reported, it is admitted - so the change didn't simply make everything ready. */
    @Test
    public void test_dispatcherSourcedFunctionIsAdmittedOnceReported() {
        final ExecutionGateService gate = gateService();
        gate.seedFunctionReadiness("mh-verify.hello-dispatcher_1.1");
        gate.recordFunctionReadiness("mh-verify.hello-dispatcher_1.1", 1L);

        assertTrue(gate.allFunctionsReady(1L, task("mh-verify.hello-dispatcher_1.1",
                EnumsApi.FunctionSourcing.dispatcher, null)));
        assertFalse(gate.allFunctionsReady(2L, task("mh-verify.hello-dispatcher_1.1",
                EnumsApi.FunctionSourcing.dispatcher, null)),
                "a report from Processor #1 says nothing about Processor #2");
    }
}
