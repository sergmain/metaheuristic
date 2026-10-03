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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.event.events.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.mcp.MhMcpToolDefinitions;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.utils.JsonUtils;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 12: the readers that displayed the whole-ExecContext task-state and
 * variable-state records read the segments.
 *
 * <p>An S1 ExecContext is created and not driven (nothing runs in MH tests unless a test drives it), so its only segment is
 * the root. Variable-state entries are written through the production segment writer
 * ({@link ExecContextSegmentVariableStateTxService}): {@code prepare} produces a variable, {@code fanout} consumes it. Then:
 * <ul>
 *   <li>{@link ExecContextSegmentReadService#variableStates} shows the consumer's input inited once the producer's output is
 *       uploaded, while the stored entry keeps its own flag (the Phase 9 obligation: derived at read time);</li>
 *   <li>{@link ExecContextSegmentReadService#changeVersion} does not move on a read and moves on a write;</li>
 *   <li>{@link ExecContextSegmentReadService#outputExt} finds the producer's extension;</li>
 *   <li>the MCP tools {@code mh_get_exec_context_graph}, {@code _task_state}, {@code _variable_state}, built on the real
 *       beans, return the derived DOT, every Task's state and the derived entries - and refuse an unknown ExecContext.</li>
 * </ul>
 *
 * <p>Correction to "created and not driven": creation itself leaves ONE asynchronous write to the root segment, see
 * {@link #awaitCreationWritesLanded}. Measuring {@code changeVersion} before it lands made the test flaky ("a read changes
 * nothing": {@code 1,5,0,0} vs {@code 1,6,0,0}), so the test waits for it first.
 *
 * <p>Logging: every step logs at INFO, prefixed {@code READS}, with the ms since creation and the thread - the ExecContext's
 * segments (id, ctx, fork, VERSION, lines, states, variable-state entries), every Task in MH_TASK and in the segments,
 * {@code changeVersion}, and each poll of the creation wait - so a failure in a suite shows what moved and when.
 */
@Slf4j
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentStateReadsTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private ExecContextSegmentVariableStateTxService segmentVariableStateTxService;
    @Autowired private ExecContextSegmentReadService segmentReadService;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextCache execContextCache;

    /** When the ExecContext of this test was created - every log line carries the ms since then. */
    private long createdAtNanos;

    private long ms() {
        return (System.nanoTime() - createdAtNanos) / 1_000_000;
    }

    /** One INFO block: {@code changeVersion}, every segment, every Task in MH_TASK and in the segments. */
    private void dump(String phase, Long ecId) {
        log.info("READS [{} ms, {}] {} - ExecContext #{} changeVersion {}", ms(), Thread.currentThread().getName(), phase, ecId,
                segmentReadService.changeVersion(ecId));
        for (Long segmentId : segmentRepository.findIdsByExecContextId(ecId)) {
            final ExecContextSegment s = segmentRepository.findById(segmentId).orElse(null);
            if (s == null) {
                log.info("READS [{} ms]   segment #{} vanished between the id read and the load", ms(), segmentId);
                continue;
            }
            final ExecContextSegmentParams p = s.getExecContextSegmentParams();
            log.info("READS [{} ms]   segment #{} ctx {} fork {} VERSION {} lines {} states {}", ms(), s.id, s.lineCtxId, s.forkTaskId,
                    s.version, p.lines.stream().map(l -> l.ctx + ":" + l.tasks.stream().map(v -> String.valueOf(v.taskId)).toList()).toList(),
                    p.states);
            for (ExecContextApiData.VariableState vs : p.variableStates) {
                log.info("READS [{} ms]     variable-state entry task #{} process {} ctx {} inputs {} outputs {}", ms(), vs.taskId, vs.process,
                        vs.taskContextId, describe(vs.inputs), describe(vs.outputs));
            }
        }
        final Map<Long, EnumsApi.TaskExecState> inSegments = segmentReadService.snapshot(ecId).states();
        for (ExecContextBaselineSupport.TaskRow r : support.rows(ecId)) {
            log.info("READS [{} ms]   task #{} {} MH_TASK {} segment {}", ms(), r.id(), r.key(), r.state(),
                    inSegments.getOrDefault(r.id(), EnumsApi.TaskExecState.NONE));
        }
    }

    private static List<String> describe(@org.jspecify.annotations.Nullable List<ExecContextApiData.VariableInfo> infos) {
        if (infos == null) {
            return List.of();
        }
        return infos.stream().map(i -> i.id + ":" + i.name + (i.inited ? ":inited" : "") + (i.nullified ? ":nullified" : "")).toList();
    }

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    private ExecContextImpl producedS1() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        createdAtNanos = System.nanoTime();
        setExecContextForTest(ec);
        log.info("READS [0 ms, {}] ExecContext #{} created and started", Thread.currentThread().getName(), ec.id);
        return ec;
    }

    /**
     * Creation is not the last write: the ExecContext's INIT Task moves on through two asynchronous hops. Production
     * publishes {@code InitVariablesTxEvent} for every produced Task ({@code TaskProducingService}); for the INIT one,
     * {@code TaskVariableInitService} (@Async, queued) moves it to its next state in MH_TASK and publishes
     * {@code UpdateTaskExecStatesInExecContextTxEvent}; {@code ExecContextTaskStateService} (@Async, queued) writes that
     * state into the Task's segment. Waits, driving nothing, until no Task is INIT and every Task's state in the segments
     * is its MH_TASK state. After that nothing writes the segments unless the test does: the schedulers are off under
     * {@code globals.testing}, PRE_INIT Tasks do not move, and this test never flushes the queued variable-state events.
     */
    private void awaitCreationWritesLanded(Long ecId) {
        final java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(() -> {
            final List<ExecContextBaselineSupport.TaskRow> rows = support.rows(ecId);
            final Map<Long, EnumsApi.TaskExecState> inSegments = segmentReadService.snapshot(ecId).states();
            final List<String> init = rows.stream().filter(r -> r.state() == EnumsApi.TaskExecState.INIT).map(r -> "#" + r.id() + " " + r.key()).toList();
            final List<String> differ = rows.stream()
                    .filter(r -> r.state() != inSegments.getOrDefault(r.id(), EnumsApi.TaskExecState.NONE))
                    .map(r -> "#" + r.id() + " " + r.key() + " MH_TASK " + r.state() + " segment " + inSegments.getOrDefault(r.id(), EnumsApi.TaskExecState.NONE))
                    .toList();
            log.info("READS [{} ms] creation wait poll {}: changeVersion {}, INIT in MH_TASK {}, MH_TASK != segment {}", ms(),
                    polls.incrementAndGet(), segmentReadService.changeVersion(ecId), init, differ);
            if (rows.stream().anyMatch(r -> r.state() == EnumsApi.TaskExecState.INIT)) {
                return false;
            }
            return rows.stream().allMatch(r -> r.state() == inSegments.getOrDefault(r.id(), EnumsApi.TaskExecState.NONE));
        });
        log.info("READS [{} ms] creation writes landed after {} poll(s)", ms(), polls.get());
    }

    private static ExecContextApiData.VariableInfo info(long id, String name, String ext) {
        final ExecContextApiData.VariableInfo vi = new ExecContextApiData.VariableInfo(id, name, EnumsApi.VariableContext.local, ext);
        vi.inited = false;
        vi.nullified = false;
        return vi;
    }

    private static ExecContextApiData.VariableState entry(Long ecId, Long taskId, String process,
                                                          List<ExecContextApiData.VariableInfo> inputs, List<ExecContextApiData.VariableInfo> outputs) {
        final ExecContextApiData.VariableState e = new ExecContextApiData.VariableState();
        e.taskId = taskId;
        e.execContextId = ecId;
        e.taskContextId = "1";
        e.process = process;
        e.inputs = new ArrayList<>(inputs);
        e.outputs = new ArrayList<>(outputs);
        return e;
    }

    private static ExecContextApiData.VariableInfo inputOf(List<ExecContextApiData.VariableState> states, Long taskId, long varId) {
        return states.stream().filter(s -> taskId.equals(s.taskId)).findFirst().orElseThrow()
                .inputs.stream().filter(i -> i.id == varId).findFirst().orElseThrow();
    }

    private MhMcpToolDefinitions mcp() {
        // positions: 4 = ExecContextCache, 6 = ExecContextSegmentReadService (041 Phase 21: three record repositories gone); the three tools read nothing else
        return new MhMcpToolDefinitions(null, null, null, execContextCache, null, segmentReadService,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static CallToolResult call(MhMcpToolDefinitions defs, String tool, Long execContextId) {
        final McpServerFeatures.SyncToolSpecification spec = defs.getAllToolSpecifications().stream()
                .filter(s -> tool.equals(s.tool().name())).findFirst()
                .orElseThrow(() -> new AssertionError(tool + " is not registered"));
        return spec.callHandler().apply(null, new CallToolRequest(tool, Map.of("execContextId", execContextId)));
    }

    private static String textOf(CallToolResult result) {
        return result.content().stream().map(c -> ((TextContent) c).text()).collect(Collectors.joining());
    }

    @Test
    public void test_derivedEntries_changeVersion_outputExt_andTheMcpReads() throws Exception {
        final ExecContextImpl ec = producedS1();
        dump("right after creation", ec.id);
        // the asynchronous state write creation leaves behind lands first - otherwise it can land between two reads below
        awaitCreationWritesLanded(ec.id);
        dump("after the creation writes landed", ec.id);
        final Map<String, Long> id = support.rows(ec.id).stream()
                .collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::processCode, ExecContextBaselineSupport.TaskRow::id));
        final Long prepare = id.get("prepare");
        final Long fanout = id.get("fanout");
        final long varId = 910_001L;
        log.info("READS [{} ms] tasks by process {}; prepare #{}, fanout #{}, variable #{}", ms(), id, prepare, fanout, varId);

        segmentVariableStateTxService.registerCreatedTasks(ec.id, List.of(
                entry(ec.id, prepare, "prepare", List.of(), List.of(info(varId, "shared", ".csv"))),
                entry(ec.id, fanout, "fanout", List.of(info(varId, "shared", null)), List.of())));
        dump("after the test's registerCreatedTasks", ec.id);

        final String v0 = segmentReadService.changeVersion(ec.id);
        final String v0again = segmentReadService.changeVersion(ec.id);
        log.info("READS [{} ms] changeVersion read twice: {} then {}", ms(), v0, v0again);
        if (!v0.equals(v0again)) {
            dump("the two reads differ", ec.id);
        }
        assertEquals(v0, v0again, "a read changes nothing");
        assertFalse(inputOf(segmentReadService.variableStates(ec.id), fanout, varId).inited, "before the upload the input is not inited");

        segmentVariableStateTxService.registerVariableStates(ec.id, List.of(new VariableUploadedEvent(ec.id, prepare, varId, false)));
        log.info("READS [{} ms] after the upload of variable #{}: changeVersion {} (was {})", ms(), varId, segmentReadService.changeVersion(ec.id), v0);
        assertNotEquals(v0, segmentReadService.changeVersion(ec.id), "the upload wrote the root segment, the version moved");

        final ExecContextApiData.VariableInfo derived = inputOf(segmentReadService.variableStates(ec.id), fanout, varId);
        log.info("READS [{} ms] fanout's input #{} derived: inited {}, nullified {}", ms(), varId, derived.inited, derived.nullified);
        assertTrue(derived.inited, "the consumer's input shows the uploaded output");
        assertFalse(derived.nullified, "not nullified, as uploaded");
        final ExecContextSegment root = Objects.requireNonNull(segmentRepository.findByExecContextIdAndLineCtxId(ec.id, "1"));
        dump("after the upload", ec.id);
        assertFalse(inputOf(root.getExecContextSegmentParams().variableStates, fanout, varId).inited,
                "the stored entry keeps its own flag - the derivation happens at read time only");

        log.info("READS [{} ms] outputExt: variable #{} -> {}, variable #999999 -> {}", ms(), varId,
                segmentReadService.outputExt(ec.id, "1", varId), segmentReadService.outputExt(ec.id, "1", 999_999L));
        assertEquals(".csv", segmentReadService.outputExt(ec.id, "1", varId), "the producer's extension");
        assertNull(segmentReadService.outputExt(ec.id, "1", 999_999L), "no entry has that output");

        // MCP, on the real beans
        final MhMcpToolDefinitions defs = mcp();

        final CallToolResult graph = call(defs, "mh_get_exec_context_graph", ec.id);
        log.info("READS [{} ms] MCP mh_get_exec_context_graph: isError {}, {} chars", ms(), graph.isError(), textOf(graph).length());
        assertNotEquals(Boolean.TRUE, graph.isError(), textOf(graph));
        final MhMcpToolDefinitions.ExecContextGraphDto graphDto = JsonUtils.getMapper().readValue(textOf(graph), MhMcpToolDefinitions.ExecContextGraphDto.class);
        log.info("READS [{} ms] MCP graph DOT: {}", ms(), graphDto.dot());
        assertEquals(ec.id, graphDto.execContextId());
        assertEquals(new TreeSet<>(id.values()), new TreeSet<>(SegmentDotUtils.parse(graphDto.dot()).nodes().keySet()),
                "the DOT has every Task of MH_TASK");

        final CallToolResult states = call(defs, "mh_get_exec_context_task_state", ec.id);
        log.info("READS [{} ms] MCP mh_get_exec_context_task_state: isError {}, {}", ms(), states.isError(), textOf(states));
        assertNotEquals(Boolean.TRUE, states.isError(), textOf(states));
        final MhMcpToolDefinitions.ExecContextTaskStateDto statesDto = JsonUtils.getMapper().readValue(textOf(states), MhMcpToolDefinitions.ExecContextTaskStateDto.class);
        assertEquals(new TreeSet<>(id.values()), new TreeSet<>(statesDto.states().keySet()), "a state for every Task");
        final Map<Long, EnumsApi.TaskExecState> stored = segmentReadService.snapshot(ec.id).states();
        log.info("READS [{} ms] segment states for the MCP comparison: {}", ms(), stored);
        statesDto.states().forEach((taskId, state) -> assertEquals(
                stored.getOrDefault(taskId, EnumsApi.TaskExecState.NONE).name(), state, "Task #" + taskId + ": the segment's state"));

        final CallToolResult vars = call(defs, "mh_get_exec_context_variable_state", ec.id);
        log.info("READS [{} ms] MCP mh_get_exec_context_variable_state: isError {}, {}", ms(), vars.isError(), textOf(vars));
        assertNotEquals(Boolean.TRUE, vars.isError(), textOf(vars));
        final MhMcpToolDefinitions.ExecContextVariableStateDto varsDto = JsonUtils.getMapper().readValue(textOf(vars), MhMcpToolDefinitions.ExecContextVariableStateDto.class);
        assertTrue(inputOf(varsDto.states(), fanout, varId).inited, "the MCP entries carry the derived input flag");

        for (String tool : List.of("mh_get_exec_context_graph", "mh_get_exec_context_task_state", "mh_get_exec_context_variable_state")) {
            final CallToolResult unknown = call(defs, tool, -1L);
            log.info("READS [{} ms] MCP {} for ExecContext #-1: isError {}, {}", ms(), tool, unknown.isError(), textOf(unknown));
            assertEquals(Boolean.TRUE, unknown.isError(), tool + ": an unknown ExecContext is an error");
            assertTrue(textOf(unknown).contains("ExecContext #-1 not found"), tool + ": " + textOf(unknown));
        }

        dump("before the invariants", ec.id);
        invariants.assertAll(ec.id);
        log.info("READS [{} ms] invariants hold - done", ms());
    }
}
