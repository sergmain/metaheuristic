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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils.EventPage;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils.EventRecord;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils.StoredEvent;
import ai.metaheuristic.ai.dispatcher.event.events.DispatcherApplicationEvent;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.commons.yaml.event.DispatcherEventYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DispatcherEventQueryUtils - which event types a query covers, how a stored event reads back, and the contextId scan.
 *
 * <p>No doubles: every stored document is produced by the production serializer from a real
 * {@link DispatcherApplicationEvent}, and the "database" is an in-memory list read through the same contract the
 * repository queries have - the events after an id, in id order, at most a chunk. That list can disagree with the
 * test; a scripted answer could not.
 */
@Execution(ExecutionMode.CONCURRENT)
public class DispatcherEventQueryUtilsTest {

    private static final int PERIOD = 202609;

    /** A stored generic event, shaped as RG profiling publishes one: a step type, a creation id as contextId, JSON params. */
    private static StoredEvent generic(long id, String event, String contextId, String params) {
        final DispatcherApplicationEvent e = new DispatcherApplicationEvent(event, null, contextId, params);
        return new StoredEvent(id, PERIOD, event, null, DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(e.dispatcherEventYaml));
    }

    /** Events 1..n of one type; odd ids belong to creation A, even ids to creation B. */
    private static List<StoredEvent> twoCreations(int n) {
        final List<StoredEvent> events = new ArrayList<>();
        for (long id = 1; id <= n; id++) {
            events.add(generic(id, "RG_MANUAL_REQ_ADD", id % 2 == 1 ? "A" : "B", "{\"n\":" + id + "}"));
        }
        return events;
    }

    /** The repository contract over an in-memory list kept in id order. */
    private static LongFunction<List<StoredEvent>> store(List<StoredEvent> events, int chunk) {
        return afterId -> events.stream().filter(e -> e.id() > afterId).limit(chunk).toList();
    }

    private static List<Long> ids(EventPage page) {
        return page.events().stream().map(EventRecord::id).toList();
    }

    // ---- resolveEventTypes

    @Test
    public void test_resolveEventTypes_neitherTypesNorPrefix_meansEveryType() {
        assertNull(DispatcherEventQueryUtils.resolveEventTypes(null, null, List::of));
        assertNull(DispatcherEventQueryUtils.resolveEventTypes(List.of(), "  ", List::of));
    }

    @Test
    public void test_resolveEventTypes_prefixIsComparedLiterally() {
        final Set<String> types = DispatcherEventQueryUtils.resolveEventTypes(null, "RG_MANUAL_REQ_",
                () -> List.of("BATCH_CREATED", "RG_MANUAL_REQ_ADD", "RG_MANUAL_REQ_OPEN", "RGXMANUAL_REQ_ADD", "TASK_FINISHED"));

        assertEquals(Set.of("RG_MANUAL_REQ_ADD", "RG_MANUAL_REQ_OPEN"), types);
    }

    @Test
    public void test_resolveEventTypes_exactTypesAndPrefix_areAUnion() {
        final Set<String> types = DispatcherEventQueryUtils.resolveEventTypes(List.of("TASK_FINISHED"), "RG_MANUAL_REQ_",
                () -> List.of("RG_MANUAL_REQ_ADD", "TASK_ASSIGNED", "TASK_FINISHED"));

        assertEquals(Set.of("TASK_FINISHED", "RG_MANUAL_REQ_ADD"), types);
    }

    @Test
    public void test_resolveEventTypes_prefixMatchingNothing_isEmptyNotNull() {
        final Set<String> types = DispatcherEventQueryUtils.resolveEventTypes(null, "NO_SUCH_", () -> List.of("TASK_FINISHED"));

        assertNotNull(types, "an empty set selects nothing; null would select every type");
        assertTrue(types.isEmpty());
    }

    @Test
    public void test_resolveEventTypes_exactTypesOnly_doNotReadTheTypesInRange() {
        final Set<String> types = DispatcherEventQueryUtils.resolveEventTypes(List.of("TASK_FINISHED"), null,
                () -> { throw new IllegalStateException("the types in range are needed only for a prefix"); });

        assertEquals(Set.of("TASK_FINISHED"), types);
    }

    // ---- toRecord

    @Test
    public void test_toRecord_genericEvent_readsBackItsContextIdAndParams() {
        final String params = "{\"startedOn\":1727000000000,\"durationUs\":1500,\"ok\":true}";
        final EventRecord r = DispatcherEventQueryUtils.toRecord(generic(7L, "RG_MANUAL_REQ_ADD", "creation-1", params));

        assertEquals(7L, r.id());
        assertEquals(PERIOD, r.period());
        assertEquals("RG_MANUAL_REQ_ADD", r.event());
        assertNull(r.companyId());
        assertEquals("creation-1", r.contextId());
        assertEquals(params, r.params());
        assertNotNull(r.createdOn());
        assertNull(r.batchData());
        assertNull(r.taskData());
        assertNull(r.error());
    }

    @Test
    public void test_toRecord_mhTaskEvent_carriesItsTaskData() {
        final DispatcherEventYaml.TaskEventData taskData = new DispatcherEventYaml.TaskEventData();
        taskData.taskId = 42L;
        taskData.execContextId = 123L;
        taskData.context = EnumsApi.FunctionExecContext.external;
        final DispatcherApplicationEvent e = new DispatcherApplicationEvent(EnumsApi.DispatcherEventType.TASK_FINISHED.name(), taskData);

        final EventRecord r = DispatcherEventQueryUtils.toRecord(new StoredEvent(8L, PERIOD, "TASK_FINISHED", 7L,
                DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(e.dispatcherEventYaml)));

        assertEquals("TASK_FINISHED", r.event());
        assertEquals(7L, r.companyId());
        assertNotNull(r.taskData());
        assertEquals(42L, r.taskData().taskId);
        assertEquals(123L, r.taskData().execContextId);
        assertEquals(EnumsApi.FunctionExecContext.external, r.taskData().context);
        assertNull(r.contextId());
        assertNull(r.params());
        assertNull(r.error());
    }

    @Test
    public void test_toRecord_unreadableDocument_isReportedNotDropped() {
        final EventRecord r = DispatcherEventQueryUtils.toRecord(new StoredEvent(9L, PERIOD, "RG_X", null, "version: 3\nno_such_field: 1\n"));

        assertEquals(9L, r.id());
        assertEquals("RG_X", r.event());
        assertNotNull(r.error());
        assertNull(r.contextId());
        assertNull(r.params());
    }

    @Test
    public void test_toRecord_blankDocument_isReportedNotDropped() {
        final EventRecord r = DispatcherEventQueryUtils.toRecord(new StoredEvent(10L, PERIOD, "RG_X", null, " "));

        assertEquals(10L, r.id());
        assertNotNull(r.error());
    }

    // ---- scan

    @Test
    public void test_scan_withoutContextId_returnsTheFirstLimitInIdOrder_andTheNextPageContinues() {
        final List<StoredEvent> events = twoCreations(5);

        final EventPage first = DispatcherEventQueryUtils.scan(0L, 3, null, 3, 1000, store(events, 3));
        assertEquals(List.of(1L, 2L, 3L), ids(first));
        assertEquals(3L, first.nextAfterId());
        assertFalse(first.complete());

        final EventPage second = DispatcherEventQueryUtils.scan(first.nextAfterId(), 3, null, 3, 1000, store(events, 3));
        assertEquals(List.of(4L, 5L), ids(second));
        assertEquals(5L, second.nextAfterId());
        assertTrue(second.complete());
    }

    @Test
    public void test_scan_contextId_keepsOnlyThatCreation_acrossChunks() {
        final EventPage page = DispatcherEventQueryUtils.scan(0L, 100, "A", 3, 1000, store(twoCreations(10), 3));

        assertEquals(List.of(1L, 3L, 5L, 7L, 9L), ids(page));
        assertEquals(10, page.scanned());
        assertEquals(10L, page.nextAfterId());
        assertTrue(page.complete());
    }

    @Test
    public void test_scan_contextId_stopsAtLimit_andTheNextPageContinuesWithoutGapOrRepeat() {
        final List<StoredEvent> events = twoCreations(10);

        final EventPage first = DispatcherEventQueryUtils.scan(0L, 2, "B", 3, 1000, store(events, 3));
        assertEquals(List.of(2L, 4L), ids(first));
        assertEquals(4L, first.nextAfterId());
        assertFalse(first.complete());

        final EventPage rest = DispatcherEventQueryUtils.scan(first.nextAfterId(), 100, "B", 3, 1000, store(events, 3));
        assertEquals(List.of(6L, 8L, 10L), ids(rest));
        assertTrue(rest.complete());
    }

    @Test
    public void test_scan_stopsAtMaxScanned_andSaysWhereToContinue() {
        final EventPage page = DispatcherEventQueryUtils.scan(0L, 100, "NO_SUCH_CREATION", 2, 4, store(twoCreations(10), 2));

        assertTrue(page.events().isEmpty());
        assertEquals(4, page.scanned());
        assertEquals(4L, page.nextAfterId());
        assertFalse(page.complete(), "events after nextAfterId were left unread");
    }

    // ---- periods

    @Test
    public void test_toPeriod_acceptsAMonth_rejectsTheRest() {
        assertEquals(202609, DispatcherEventQueryUtils.toPeriod("fromPeriod", 202609L));
        assertEquals(202612, DispatcherEventQueryUtils.toPeriod("fromPeriod", 202612L));
        for (long bad : new long[]{202613L, 202600L, 20269L, 2026090L, 0L, -202609L}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> DispatcherEventQueryUtils.toPeriod("fromPeriod", bad));
            assertTrue(e.getMessage().contains("'fromPeriod'"), e.getMessage());
        }
    }

    @Test
    public void test_requireRange_rejectsFromAfterTo() {
        assertDoesNotThrow(() -> DispatcherEventQueryUtils.requireRange(202608, 202609));
        assertDoesNotThrow(() -> DispatcherEventQueryUtils.requireRange(202609, 202609));
        assertThrows(IllegalArgumentException.class, () -> DispatcherEventQueryUtils.requireRange(202610, 202609));
    }
}
