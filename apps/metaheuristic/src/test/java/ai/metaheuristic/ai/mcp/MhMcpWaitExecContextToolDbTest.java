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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextBaselineSupport;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentReadService;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.SegmentFixtureShapes;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.api.EnumsApi;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code mh_wait_exec_context} against a real ExecContext: the probe the handler builds from the real ExecContext
 * cache and {@code ExecContextSegmentReadService.changeVersion}, and the Task states it reads from the real segments.
 * The wait loop itself is covered Spring-less by {@link ExecContextWaitUtilsTest}; this pins that the real reads behave
 * the way that loop assumes - a version that stays put while nothing happens, moves when something does, and counts
 * that agree with {@code MH_TASK}.
 *
 * <p>The MCP tool bean exists only under the 'mcp' profile, which this context doesn't run, so the definitions are
 * built here with the two real services the tool reads; every other collaborator is null and reaching one would
 * fail the test.
 *
 * @author Serge
 * Date: 10/10/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class MhMcpWaitExecContextToolDbTest extends PreparingSourceCode {

    private static final String TOOL = "mh_wait_exec_context";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextSegmentReadService segmentReadService;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private TaskWithInternalContextEventService taskWithInternalContextEventService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    private CallToolResult call(Map<String, Object> arguments) {
        return new MhMcpToolDefinitions(null, null, null, execContextCache, null, segmentReadService, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                .getAllToolSpecifications().stream()
                .filter(s -> TOOL.equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("not registered: " + TOOL))
                .callHandler().apply(null, new CallToolRequest(TOOL, arguments));
    }

    private static String textOf(CallToolResult result) {
        return result.content().stream().map(c -> ((TextContent) c).text()).collect(Collectors.joining());
    }

    private MhMcpToolDefinitions.WaitExecContextDto await(Long execContextId, String until, @Nullable String sinceVersion, int maxSeconds) {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("execContextId", execContextId);
        arguments.put("until", until);
        arguments.put("maxSeconds", maxSeconds);
        if (sinceVersion != null) {
            arguments.put("sinceVersion", sinceVersion);
        }
        final CallToolResult result = call(arguments);
        assertNotEquals(Boolean.TRUE, result.isError(), textOf(result));
        return JSON.readValue(textOf(result), MhMcpToolDefinitions.WaitExecContextDto.class);
    }

    private PreparingData.PreparingSourceCodeData prepare() {
        return preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
    }

    private ExecContextImpl createAndStart(PreparingData.PreparingSourceCodeData data) {
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        // the harness's per-test ExecContext pointer follows this run
        setExecContextForTest(ec);
        return ec;
    }

    private static int total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    public void test_versionStaysWhileNothingRuns_andMovesWhenTheExecContextStops() {
        final PreparingData.PreparingSourceCodeData data = prepare();
        // suspended before the start, as ExecContextBaselineSupport.runStepByStep does: nothing queued gets to run
        taskWithInternalContextEventService.TASK_WITH_INTERNAL_CTX_MTQ.registerProcessSuspender(() -> true);
        try {
            final ExecContextImpl ec = createAndStart(data);
            support.settle(ec.id);

            final MhMcpToolDefinitions.WaitExecContextDto first = await(ec.id, "STATE_CHANGED", null, 1);
            assertTrue(first.met(), "without sinceVersion STATE_CHANGED answers at once");
            assertFalse(first.terminal());
            assertEquals("STARTED", first.stateName());
            assertTrue(first.version().startsWith(EnumsApi.ExecContextState.STARTED.code + ":"), first.version());
            assertEquals(support.rows(ec.id).size(), total(first.taskStateCounts()),
                    "every Task of the ExecContext must be counted once, counts: " + first.taskStateCounts());

            final MhMcpToolDefinitions.WaitExecContextDto idle = await(ec.id, "STATE_CHANGED", first.version(), 1);
            assertFalse(idle.met(), "the queue is suspended, nothing ran, so the version must not move: "
                    + first.version() + " -> " + idle.version());
            assertTrue(idle.timedOut());
            assertEquals(first.version(), idle.version());
            assertTrue(idle.waitedMillis() >= 1000, "waited " + idle.waitedMillis());

            execContextTopLevelService.execContextTargetState(ec.id, EnumsApi.ExecContextState.STOPPED, ec.companyId);

            final MhMcpToolDefinitions.WaitExecContextDto stopped = await(ec.id, "STATE_CHANGED", first.version(), 1);
            assertTrue(stopped.met(), "stopping is a change: " + first.version() + " -> " + stopped.version());
            assertTrue(stopped.terminal());
            assertEquals("STOPPED", stopped.stateName());
            assertNotEquals(first.version(), stopped.version());
        }
        finally {
            taskWithInternalContextEventService.TASK_WITH_INTERNAL_CTX_MTQ.deRegisterProcessSuspender();
        }
    }

    @Test
    public void test_finishedExecContext_terminalIsMetAtOnce_withCountsAgreeingWithMhTask() {
        final PreparingData.PreparingSourceCodeData data = prepare();
        final Long ecId = support.runStepByStep(() -> createAndStart(data).id, 80).execContextId();
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(ecId), "precondition: the S1 run must finish");
        final Map<String, Integer> fromMhTask = new TreeMap<>();
        support.rows(ecId).forEach(row -> fromMhTask.merge(row.state().name(), 1, Integer::sum));

        final MhMcpToolDefinitions.WaitExecContextDto r = await(ecId, "TERMINAL", null, 5);

        assertTrue(r.met());
        assertTrue(r.terminal());
        assertFalse(r.timedOut());
        assertEquals("FINISHED", r.stateName());
        assertTrue(r.waitedMillis() < 1000, "a finished ExecContext answers at the first probe, waited " + r.waitedMillis());
        assertEquals(fromMhTask, r.taskStateCounts(), "the counts read from the segments must agree with MH_TASK at FINISHED");
        assertEquals(List.of(), r.firstErrorTaskIds());
    }

    @Test
    public void test_unknownExecContext_isAToolError() {
        final CallToolResult r = call(Map.of("execContextId", Long.MAX_VALUE, "until", "TERMINAL"));

        assertEquals(Boolean.TRUE, r.isError());
        assertEquals("ExecContext #" + Long.MAX_VALUE + " not found", textOf(r));
    }
}
