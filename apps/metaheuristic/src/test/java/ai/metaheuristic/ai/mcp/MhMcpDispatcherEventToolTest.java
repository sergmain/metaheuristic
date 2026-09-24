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
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.DispatcherEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils;
import ai.metaheuristic.ai.dispatcher.event.events.DispatcherApplicationEvent;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherEventRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mh_list_dispatcher_event_types and mh_list_dispatcher_events on the V3 harness - real Spring context, real H2,
 * real MH_EVENT records.
 *
 * <p>What these tools add is database work: a group-by over EVENT and PERIOD, and value selects ordered by id and
 * cut by a page size. Only real records can show that the JPQL is right, that the range is inclusive at both ends,
 * and that paging by id neither skips nor repeats an event. Each tool is called by its name and its own result
 * shape is read back, which is also what tells a swapped handler apart - both tools declare fromPeriod first, so
 * {@link MhMcpToolWiringTest}'s argument-name probe can't.
 *
 * <p>Events are stored the way {@code DispatcherEventService.handleAsync} stores them: type and month as columns,
 * the rest as the serialized document. Every event type comes from {@link SharedItEnv#uniqueCode}, and the global
 * read of event types is filtered to this test's own types - the shared DB holds every other test's events too.
 * The subject is built by hand with nulls for the collaborators these tools never reach.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class MhMcpDispatcherEventToolTest extends MhSharedItTest {

    private static final String TYPES_TOOL = "mh_list_dispatcher_event_types";
    private static final String EVENTS_TOOL = "mh_list_dispatcher_events";

    @Autowired private DispatcherEventRepository dispatcherEventRepository;
    @Autowired private DispatcherEventQueryService dispatcherEventQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CallToolResult call(String toolName, Map<String, Object> arguments) {
        final MhMcpToolDefinitions definitions = new MhMcpToolDefinitions(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, dispatcherEventQueryService);
        final McpServerFeatures.SyncToolSpecification spec = definitions.getAllToolSpecifications().stream()
                .filter(s -> toolName.equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(toolName + " is not registered"));
        return spec.callHandler().apply(null, new CallToolRequest(toolName, arguments));
    }

    private static String textOf(CallToolResult result) {
        return result.content().stream()
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining());
    }

    private DispatcherEventQueryUtils.EventPage page(CallToolResult result) {
        assertNotEquals(Boolean.TRUE, result.isError(), textOf(result));
        return objectMapper.readValue(textOf(result), DispatcherEventQueryUtils.EventPage.class);
    }

    private static List<Long> ids(DispatcherEventQueryUtils.EventPage page) {
        return page.events().stream().map(DispatcherEventQueryUtils.EventRecord::id).toList();
    }

    /** One event stored as DispatcherEventService.handleAsync stores it: type and month as columns, the rest in PARAMS. */
    private DispatcherEvent store(int period, String event, @Nullable String contextId, @Nullable String params) {
        final DispatcherApplicationEvent e = new DispatcherApplicationEvent(event, null, contextId, params);
        final DispatcherEvent le = new DispatcherEvent();
        le.period = period;
        le.event = event;
        le.updateParams(e.dispatcherEventYaml);
        return dispatcherEventRepository.save(le);
    }

    @Test
    public void test_listTypes_countsEachTypeInTheRange_bothEndsInclusive() {
        final String a = SharedItEnv.uniqueCode("MCP_EVT_A");
        final String b = SharedItEnv.uniqueCode("MCP_EVT_B");
        store(202607, a, null, null);   // before the range
        store(202608, a, null, null);
        store(202608, a, null, null);
        store(202609, a, null, null);
        store(202609, b, null, null);
        store(202610, b, null, null);   // after the range

        final CallToolResult result = call(TYPES_TOOL, Map.of("fromPeriod", 202608, "toPeriod", 202609));

        assertNotEquals(Boolean.TRUE, result.isError(), textOf(result));
        final MhMcpToolDefinitions.DispatcherEventTypesDto dto =
                objectMapper.readValue(textOf(result), MhMcpToolDefinitions.DispatcherEventTypesDto.class);
        assertEquals(202608, dto.fromPeriod());
        assertEquals(202609, dto.toPeriod());
        final Map<String, DispatcherEventQueryUtils.EventTypeStat> own = dto.types().stream()
                .filter(t -> t.event().equals(a) || t.event().equals(b))
                .collect(Collectors.toMap(DispatcherEventQueryUtils.EventTypeStat::event, t -> t));
        assertEquals(new DispatcherEventQueryUtils.EventTypeStat(a, 3, 202608, 202609), own.get(a));
        assertEquals(new DispatcherEventQueryUtils.EventTypeStat(b, 1, 202609, 202609), own.get(b));
    }

    @Test
    public void test_listEvents_exactTypes_everyEventOfTheRangeInIdOrder() {
        final String t = SharedItEnv.uniqueCode("MCP_EVT_T");
        final DispatcherEvent e1 = store(202609, t, "x", "1");
        final DispatcherEvent e2 = store(202609, t, "y", "2");
        store(202610, t, "z", "3");     // after the range

        final DispatcherEventQueryUtils.EventPage page = page(call(EVENTS_TOOL, Map.of(
                "fromPeriod", 202609, "toPeriod", 202609, "eventTypes", List.of(t))));

        assertEquals(List.of(e1.id, e2.id), ids(page));
        assertEquals(List.of("x", "y"), page.events().stream().map(DispatcherEventQueryUtils.EventRecord::contextId).toList());
        assertTrue(page.complete());
    }

    @Test
    public void test_listEvents_prefixAndContextId_selectOneCreation_pagedByAfterId() {
        final String prefix = SharedItEnv.uniqueCode("MCP_EVT_P") + "_";
        final String add = prefix + "ADD";
        final String open = prefix + "OPEN";
        final String other = SharedItEnv.uniqueCode("MCP_EVT_Q");
        final DispatcherEvent c1Add = store(202609, add, "c1", "{\"durationUs\":10}");
        store(202609, add, "c2", "{\"durationUs\":20}");
        store(202609, other, "c1", "{\"durationUs\":30}");    // creation c1, but not a type of the prefix
        final DispatcherEvent c1Open = store(202609, open, "c1", "{\"durationUs\":40}");
        store(202609, open, "c2", "{\"durationUs\":50}");

        final Map<String, Object> args = new HashMap<>(Map.of(
                "fromPeriod", 202609, "toPeriod", 202609, "eventTypePrefix", prefix, "contextId", "c1", "limit", 1));

        final DispatcherEventQueryUtils.EventPage first = page(call(EVENTS_TOOL, args));
        assertEquals(List.of(c1Add.id), ids(first));
        assertEquals(add, first.events().getFirst().event());
        assertEquals("c1", first.events().getFirst().contextId());
        assertEquals("{\"durationUs\":10}", first.events().getFirst().params());
        assertFalse(first.complete());

        args.put("afterId", first.nextAfterId());
        final DispatcherEventQueryUtils.EventPage second = page(call(EVENTS_TOOL, args));
        assertEquals(List.of(c1Open.id), ids(second));
        assertEquals(open, second.events().getFirst().event());
        assertEquals("{\"durationUs\":40}", second.events().getFirst().params());

        args.put("afterId", second.nextAfterId());
        final DispatcherEventQueryUtils.EventPage third = page(call(EVENTS_TOOL, args));
        assertTrue(third.events().isEmpty());
        assertTrue(third.complete());
    }

    @Test
    public void test_listEvents_invalidMonth_isAToolErrorNamingTheParameter() {
        final CallToolResult result = call(EVENTS_TOOL, Map.of("fromPeriod", 202613, "toPeriod", 202613));

        assertEquals(Boolean.TRUE, result.isError());
        assertTrue(textOf(result).contains("'fromPeriod'"), textOf(result));
    }
}
