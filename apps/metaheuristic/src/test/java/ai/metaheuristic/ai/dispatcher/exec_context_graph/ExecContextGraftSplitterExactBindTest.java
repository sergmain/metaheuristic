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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSL v2 nested-splitter auto-bind: the enclosing dynamic {@code mh.batch-line-splitter} writes its
 * per-line output ({@code reqJson}) at the EXACT per-line ctx. {@code resolveEnclosingDynamicSplitterBinding}
 * must carry THAT per-line value into the grafted line ctx. After the DSL v2 migration dropped the
 * per-level {@code {L}} suffix, EVERY level shares the single name {@code reqJson}, so an ancestry-walking
 * lookup can climb from the per-line ctx to an ANCESTOR level that carries the same name in
 * {@code ExecContextVariableState} (the enclosing splitter's per-line row is written in the same tx and is
 * not yet in the async state), returning the ANCESTOR's content. That is why every grafted rung stored the
 * top requirement's content (all requirements identical).
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Transactional
public class ExecContextGraftSplitterExactBindTest extends MhSharedItTest {

    @Autowired private VariableTxService variableTxService;
    @Autowired private ExecContextVariableStateRepository execContextVariableStateRepository;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextGraftService execContextGraftService;

    private static final String OUTPUT_VAR = "reqJson";
    private static final String SPLITTER_PROCESS_CODE = "mh.batch-line-splitter-r";
    // per-line ctx of the enclosing dynamic splitter (child level)
    private static final String CHILD_CTX = "1,2,5|0#1";
    // the ancestor on CHILD_CTX's parent-walk chain (deriveParentTaskContextId("1,2,5|0#1") -> "1,2#0")
    private static final String PARENT_CTX = "1,2#0";

    private Long setupExecContextWithSplitterProcess() {
        ExecContextVariableState ecvs = new ExecContextVariableState();
        ecvs.createdOn = System.currentTimeMillis();
        ExecContextApiData.ExecContextVariableStates info = new ExecContextApiData.ExecContextVariableStates();
        ecvs.updateParams(info);
        ecvs = execContextVariableStateRepository.save(ecvs);

        ExecContextImpl ec = new ExecContextImpl();
        ec.sourceCodeId = 1L;
        ec.companyId = 7L;
        ec.accountId = 1L;
        ec.createdOn = System.currentTimeMillis();
        ec.state = EnumsApi.ExecContextState.STARTED.code;
        ec.execContextVariableStateId = ecvs.id;
        ec.execContextGraphId = 0L;
        ec.execContextTaskStateId = 0L;

        ExecContextParamsYaml pyaml = new ExecContextParamsYaml();
        ExecContextParamsYaml.Process splitter = new ExecContextParamsYaml.Process();
        splitter.processName = "splitter";
        splitter.processCode = SPLITTER_PROCESS_CODE;
        splitter.internalContextId = "1";
        splitter.function = new ExecContextParamsYaml.FunctionDefinition("mh.batch-line-splitter");
        splitter.metas.add(Map.of("output-variable", OUTPUT_VAR));
        pyaml.processes.add(splitter);
        ec.updateParams(pyaml);

        ec = execContextCache.save(ec);
        ecvs.execContextId = ec.id;
        execContextVariableStateRepository.save(ecvs);
        return ec.id;
    }

    private Variable createVar(String taskContextId, Long ecId, String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        return variableTxService.createInitializedTx(new ByteArrayInputStream(data), data.length,
                OUTPUT_VAR, null, ecId, taskContextId, EnumsApi.VariableType.text);
    }

    private void registerInEcvs(Long ecId, String taskContextId, Long variableId) {
        ExecContextImpl ec = execContextCache.findById(ecId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        ExecContextApiData.VariableInfo vi = new ExecContextApiData.VariableInfo(variableId, OUTPUT_VAR, EnumsApi.VariableContext.local, ".txt");
        info.states.add(new ExecContextApiData.VariableState(1L, 0L, 0L, taskContextId, "p", "f", null, List.of(vi)));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);
    }

    private Long createSplitterTask(Long ecId) {
        TaskImpl task = new TaskImpl();
        task.execContextId = ecId;
        task.execState = EnumsApi.TaskExecState.NONE.value;
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = ecId;
        tpy.task.taskContextId = "1";
        tpy.task.processCode = SPLITTER_PROCESS_CODE;
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.code = "mh.batch-line-splitter";
        fc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        tpy.task.function = fc;
        task.updateParams(tpy);
        task = taskRepository.save(task);
        return task.id;
    }

    @Test
    public void test_CT_enclosingSplitterPerLineOutput_maskedByAncestor() {
        Long ecId = setupExecContextWithSplitterProcess();

        // the enclosing dynamic splitter wrote its per-line output at the EXACT per-line ctx
        // (DB only; the async ExecContextVariableState does not yet carry it)
        createVar(CHILD_CTX, ecId, "CHILD-CONTENT");

        // a completed ancestor level carries the SAME-named variable (reqJson) in ECVS, on CHILD_CTX's parent walk
        Variable parent = createVar(PARENT_CTX, ecId, "PARENT-CONTENT");
        registerInEcvs(ecId, PARENT_CTX, parent.id);

        Long splitterTaskId = createSplitterTask(ecId);

        ExecContextGraftService.InputBinding binding =
                execContextGraftService.resolveEnclosingDynamicSplitterBinding(ecId, splitterTaskId, CHILD_CTX);

        assertNotNull(binding, "the enclosing splitter's per-line output must resolve to a binding");
        assertEquals(OUTPUT_VAR, binding.name());

        // Green-1 (characterization): the ancestry-walking resolver returns the ANCESTOR's same-named reqJson
        // from ECVS, MASKING the exact per-line value present in DB. This is the collapse: every grafted rung
        // is auto-bound to the ancestor's content, so all requirements end up identical.
        assertEquals("CHILD-CONTENT", binding.value(),
                "the enclosing splitter's per-line output at the exact per-line ctx must win over any same-named ancestor");
    }
}
