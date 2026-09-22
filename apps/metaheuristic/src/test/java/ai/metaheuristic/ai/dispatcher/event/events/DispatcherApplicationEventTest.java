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

package ai.metaheuristic.ai.dispatcher.event.events;

import ai.metaheuristic.ai.dispatcher.beans.DispatcherEvent;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generic dispatcher event - a type which MH doesn't have to know, with its payload in params.
 */
@Execution(ExecutionMode.CONCURRENT)
public class DispatcherApplicationEventTest {

    @Test
    public void test_genericConstructor_setsTypeContextParamsAndCreatedOn() {
        final LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DispatcherApplicationEvent e = new DispatcherApplicationEvent("RG_CUSTOM_EVENT", 42L, "ctx-1", "snapshotId: 17");
        final LocalDateTime after = LocalDateTime.now();

        assertEquals(42L, e.companyUniqueId);
        final DispatcherEventYaml y = e.dispatcherEventYaml;
        assertEquals("RG_CUSTOM_EVENT", y.event);
        assertEquals("ctx-1", y.contextId);
        assertEquals("snapshotId: 17", y.params);
        assertNull(y.batchData);
        assertNull(y.taskData);

        // DispatcherEventService.handleAsync() derives the period of an MH_EVENT record from createdOn with this formatter
        final LocalDateTime createdOn = LocalDateTime.parse(y.createdOn, CommonConsts.EVENT_DATE_TIME_FORMATTER);
        assertFalse(createdOn.isBefore(before), "createdOn " + createdOn + " is before " + before);
        assertFalse(createdOn.isAfter(after), "createdOn " + createdOn + " is after " + after);
    }

    @Test
    public void test_genericEvent_survivesDispatcherEventParams() {
        DispatcherApplicationEvent e = new DispatcherApplicationEvent("RG_CUSTOM_EVENT", 42L, "ctx-1", "{\"snapshotId\":17}");

        // updateParams() is what DispatcherEventService.handleAsync() stores with, getDispatcherEventYaml() is the entity's read side
        DispatcherEvent le = new DispatcherEvent();
        le.updateParams(e.dispatcherEventYaml);

        assertEquals(e.dispatcherEventYaml, le.getDispatcherEventYaml());
    }

    @Test
    public void test_genericEvent_withoutPayload() {
        // a literal null as the last argument would be ambiguous with the batch constructor
        DispatcherApplicationEvent e = new DispatcherApplicationEvent("RG_CUSTOM_EVENT", null, null, (String) null);

        DispatcherEvent le = new DispatcherEvent();
        le.updateParams(e.dispatcherEventYaml);

        final DispatcherEventYaml restored = le.getDispatcherEventYaml();
        assertEquals("RG_CUSTOM_EVENT", restored.event);
        assertNull(restored.params);
        assertNull(restored.contextId);
        assertNull(restored.batchData);
        assertNull(restored.taskData);
        assertNull(e.companyUniqueId);
    }
}
