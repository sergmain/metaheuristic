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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.utils.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-5 (b) acceptance IT: {@link ExecContextGraftService#attachGroup} resolves a first-class v6
 * group BY NAME and produces the SAME graft as the v0 by-reference path (025-MHSC-DSL-V2-PLAN, Phase 5).
 *
 * <p>V3 harness (DESCRIPTION-TEST-PIPELINE-V2-V3): extends PreparingSourceCode -> PreparingCore ->
 * MhSharedItTest (shared Spring context + H2 DB; no @DirtiesContext; cleanup inherited).
 *
 * <p>The proof is a direct A/B comparison in ONE ExecContext:
 * <ol>
 *   <li>graft the target's own sub-processes BY REFERENCE ({@link ExecContextGraftService.GroupRef#fromTargetSubProcesses()});</li>
 *   <li>mint a v6 group whose body IS those same sub-processes, inject it into the EC params, then graft it BY NAME;</li>
 *   <li>assert both grafts have the SAME head process code, the SAME multiset of process codes on the
 *       grafted line, both are SKIPPED terminal, and both heads derive back to the target - i.e. the
 *       body source (target-borrow vs named group) does not change the graft. Line isolation keeps the
 *       two grafts independent (sibling lines are dropped from each other's terminal wiring).</li>
 * </ol>
 * The by-name graft additionally materializes a write-once output, proving the PLACE_NOW output path
 * works through the by-name resolution too.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@AutoConfigureCache
public class ExecContextGraftByNameGroupIT extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private InternalFunctionService internalFunctionService;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private VariableRepository variableRepository;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_attachGroup_byName_producesSameGraftAsByReference() {
        // 1. create EC + produce real tasks WITHOUT starting (STOPPED - the graft target state).
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long ecId = getExecContextForTest().id;

        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, () ->
                                txSupportForTestingService.produceTasksWithoutStarting(getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        // 2. find a target task whose getSubProcesses resolves a non-empty SEQUENTIAL body.
        final ExecContextImpl ec = getExecContextForTest();
        final ExecContextApiData.SimpleExecContext sec = ec.asSimple();
        Long targetTaskId = null;
        InternalFunctionData.ExecutionContextData ecdTarget = null;
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            InternalFunctionData.ExecutionContextData ecd =
                    internalFunctionService.getSubProcesses(sec, t.getTaskParamsYaml(), t.id);
            if (ecd.internalFunctionProcessingResult.processing == Enums.InternalFunctionProcessing.ok
                    && !ecd.subProcesses.isEmpty()
                    && ecd.process.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                targetTaskId = t.id;
                ecdTarget = ecd;
                break;
            }
        }
        assertNotNull(targetTaskId, "no task with a graftable sub-process body found in the produced EC");
        final String targetCtx = Objects.requireNonNull(
                taskRepository.findByIdReadOnly(targetTaskId)).getTaskParamsYaml().task.taskContextId;

        // the body the target defines - the yardstick both grafts must reproduce.
        final List<String> bodyCodesSorted = ecdTarget.subProcesses.stream()
                .map(v -> v.process).sorted().toList();
        final String expectedHeadCode = ecdTarget.subProcesses.get(0).process;

        // 3. GRAFT A - by reference (the v0 path: the target's own sub-processes), no outputs.
        ExecContextGraftService.GraftResult grByRef = execContextGraftService.attachGroup(
                ecId, targetTaskId,
                ExecContextGraftService.GroupRef.fromTargetSubProcesses(),
                List.of(), List.of(),
                ExecContextGraftService.Driver.PLACE_NOW);
        assertNotNull(grByRef.headTaskId());
        assertNotNull(grByRef.lineCtxId());

        // 4. mint a v6 group whose body IS the target's sub-processes, inject it into the EC params.
        final ExecContextParamsYaml.Group group = new ExecContextParamsYaml.Group("grp-mirror");
        for (ExecContextApiData.ProcessVertex v : ecdTarget.subProcesses) {
            group.body.add(Objects.requireNonNull(sec.paramsYaml.findProcess(v.process),
                    "body process '" + v.process + "' must be resolvable in the EC params"));
        }
        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                txSupportForTestingService.addGroupToExecContextParams(ecId, group));

        // 5. GRAFT B - by name, with one write-once output.
        final byte[] outVal = "by-name-value".getBytes(StandardCharsets.UTF_8);
        ExecContextGraftService.GraftResult grByName = execContextGraftService.attachGroup(
                ecId, targetTaskId,
                new ExecContextGraftService.GroupRef("grp-mirror"),
                List.of(),
                List.of(new ExecContextGraftService.OutputMaterialization("graftOutByName", outVal)),
                ExecContextGraftService.Driver.PLACE_NOW);
        assertNotNull(grByName.headTaskId());
        assertNotNull(grByName.lineCtxId());

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        // 6a. the two grafts landed on DISTINCT fresh sibling lines (no collision).
        assertNotEquals(grByRef.lineCtxId(), grByName.lineCtxId(),
                "the by-name graft must occupy its own fresh sibling line ctx");

        // 6b. SAME head process code - identity of the grafted line's root is the same either way.
        assertEquals(expectedHeadCode, headProcessCode(ecId, grByRef.headTaskId()));
        assertEquals(expectedHeadCode, headProcessCode(ecId, grByName.headTaskId()));

        // 6c. SAME body - the multiset of process codes on each grafted line equals the target's body.
        assertEquals(bodyCodesSorted, lineProcessCodesSorted(ecId, grByRef.lineCtxId()),
                "by-reference line must reproduce the target's body");
        assertEquals(bodyCodesSorted, lineProcessCodesSorted(ecId, grByName.lineCtxId()),
                "by-name line must reproduce the SAME body as by-reference");

        // 6d. both grafted lines are SKIPPED terminal (PLACE_NOW), event-free.
        assertLineSkipped(ecId, grByRef.lineCtxId(), grByRef.headTaskId());
        assertLineSkipped(ecId, grByName.lineCtxId(), grByName.headTaskId());

        // 6e. both heads derive back to the target (the rebase kept the nesting invariant).
        assertTrue(derivesToTarget(grByRef.lineCtxId(), targetCtx),
                "by-reference head must derive up to the target");
        assertTrue(derivesToTarget(grByName.lineCtxId(), targetCtx),
                "by-name head must derive up to the target");

        // 6f. the by-name PLACE_NOW output is materialized EXACTLY ONCE at its line ctx (write-once).
        Variable outVar = variableRepository.findByNameAndTaskContextIdAndExecContextId(
                "graftOutByName", grByName.lineCtxId(), ecId);
        assertNotNull(outVar, "by-name output must be materialized at the grafted line ctx " + grByName.lineCtxId());
        assertEquals(1, variableRepository.findByExecContextIdAndNames(ecId, List.of("graftOutByName")).size(),
                "by-name output must be written exactly once (write-once, no duplicate key)");
    }

    private String headProcessCode(Long ecId, Long headTaskId) {
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            if (t.id.equals(headTaskId)) {
                return t.getTaskParamsYaml().task.processCode;
            }
        }
        return null;
    }

    private List<String> lineProcessCodesSorted(Long ecId, String lineCtxId) {
        List<String> codes = new ArrayList<>();
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            if (lineCtxId.equals(t.getTaskParamsYaml().task.taskContextId)) {
                codes.add(t.getTaskParamsYaml().task.processCode);
            }
        }
        codes.sort(String::compareTo);
        return codes;
    }

    private void assertLineSkipped(Long ecId, String lineCtxId, Long headTaskId) {
        assertEquals(EnumsApi.TaskExecState.SKIPPED,
                preparingSourceCodeService.findTaskState(getExecContextForTest(), headTaskId),
                "grafted head must be SKIPPED (terminal)");
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            if (lineCtxId.equals(t.getTaskParamsYaml().task.taskContextId)) {
                assertEquals(EnumsApi.TaskExecState.SKIPPED,
                        preparingSourceCodeService.findTaskState(getExecContextForTest(), t.id),
                        "every task on the grafted line must be SKIPPED; task #" + t.id);
            }
        }
    }

    private boolean derivesToTarget(String lineCtxId, String targetCtx) {
        String ctx = lineCtxId;
        for (int i = 0; i < 32 && ctx != null; i++) {
            if (targetCtx.equals(ctx)) {
                return true;
            }
            ctx = ContextUtils.deriveParentTaskContextId(ctx);
        }
        return targetCtx.equals(ctx);
    }
}
