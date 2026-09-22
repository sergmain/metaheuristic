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

package ai.metaheuristic.commons.yaml.event;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-versioning of {@link DispatcherEventYaml}.
 *
 * <p>The documents below have the shape of what is already stored in MH_EVENT.PARAMS. The event type
 * is read through {@code String.valueOf(p.event)} so that the same assertions hold whether the field
 * is typed as {@code EnumsApi.DispatcherEventType} or as {@code String} — stored documents must keep
 * loading, unchanged, across that type change.
 */
@Execution(ExecutionMode.CONCURRENT)
public class DispatcherEventYamlUtilsTest {

    private static final String V1_TASK_FINISHED = """
            version: 1
            createdOn: 21/09/2026 10:11:12
            event: TASK_FINISHED
            taskData:
              execContextId: 123
              processorId: 17
              taskId: 42
            """;

    private static final String V2_BATCH_CREATED = """
            version: 2
            batchData:
              batchId: 42
              companyId: 7
              execContextId: 123
              filename: mh.batch-uid-1
              username: user-1
            contextId: 3f1c2b9e-0000-4000-8000-000000000001
            createdOn: 21/09/2026 10:11:12
            event: BATCH_CREATED
            """;

    private static final String V2_TASK_ERROR = """
            version: 2
            createdOn: 21/09/2026 10:11:12
            event: TASK_ERROR
            taskData:
              context: internal
              coreId: 5
              execContextId: 123
              funcCode: mh.nop
              taskId: 42
            """;

    private static final String V3_CUSTOM_EVENT = """
            version: 3
            contextId: ctx-1
            createdOn: 22/09/2026 10:11:12
            event: RG_CUSTOM_EVENT
            params: 'snapshotId: 17'
            """;

    // ---- characterization: documents already stored in MH_EVENT.PARAMS

    @Test
    public void test_readV1Document_taskFinished() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V1_TASK_FINISHED);

        assertEquals("TASK_FINISHED", String.valueOf(p.event));
        assertEquals("21/09/2026 10:11:12", p.createdOn);
        assertNull(p.contextId);
        assertNull(p.batchData);
        assertNotNull(p.taskData);
        assertEquals(17L, p.taskData.coreId, "V1 taskData.processorId must be upgraded into coreId");
        assertEquals(42L, p.taskData.taskId);
        assertEquals(123L, p.taskData.execContextId);
        assertNull(p.taskData.context);
        assertNull(p.taskData.funcCode);
    }

    @Test
    public void test_readV2Document_batchCreated() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V2_BATCH_CREATED);

        assertEquals("BATCH_CREATED", String.valueOf(p.event));
        assertEquals("21/09/2026 10:11:12", p.createdOn);
        assertEquals("3f1c2b9e-0000-4000-8000-000000000001", p.contextId);
        assertNull(p.taskData);
        assertNotNull(p.batchData);
        assertEquals(42L, p.batchData.batchId);
        assertEquals(7L, p.batchData.companyId);
        assertEquals(123L, p.batchData.execContextId);
        assertEquals("mh.batch-uid-1", p.batchData.filename);
        assertEquals("user-1", p.batchData.username);
        assertNull(p.batchData.size);
    }

    @Test
    public void test_readV2Document_taskErrorKeepsFunctionContext() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V2_TASK_ERROR);

        assertEquals("TASK_ERROR", String.valueOf(p.event));
        assertNull(p.batchData);
        assertNotNull(p.taskData);
        assertEquals(EnumsApi.FunctionExecContext.internal, p.taskData.context);
        assertEquals("mh.nop", p.taskData.funcCode);
        assertEquals(5L, p.taskData.coreId);
        assertEquals(42L, p.taskData.taskId);
        assertEquals(123L, p.taskData.execContextId);
    }

    @Test
    public void test_serialize_eventIsWrittenAsPlainScalar() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V2_BATCH_CREATED);

        final String s = DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(p);

        assertTrue(s.contains("event: BATCH_CREATED"), "event must be written as a plain scalar, yaml:\n" + s);
        assertFalse(s.contains("!!"), "no explicit type tag may be written, yaml:\n" + s);
    }

    @Test
    public void test_roundTrip_preservesEventAndBatchData() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V2_BATCH_CREATED);

        DispatcherEventYaml restored = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(p));

        assertEquals("BATCH_CREATED", String.valueOf(restored.event));
        assertEquals(p.contextId, restored.contextId);
        assertEquals(p.createdOn, restored.createdOn);
        assertEquals(p.batchData, restored.batchData);
        assertNull(restored.taskData);
    }

    // ---- new behaviour: an event type which MH doesn't know

    @Test
    public void test_readV3Document_customEventType() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V3_CUSTOM_EVENT);

        assertEquals("RG_CUSTOM_EVENT", String.valueOf(p.event));
        assertEquals("ctx-1", p.contextId);
        assertEquals("22/09/2026 10:11:12", p.createdOn);
        assertEquals("snapshotId: 17", p.params);
        assertNull(p.batchData);
        assertNull(p.taskData);
    }

    @Test
    public void test_upgradeFromV2_paramsIsNullAndVersion3IsWritten() {
        DispatcherEventYaml p = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(V2_BATCH_CREATED);

        assertNull(p.params);
        final String s = DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(p);
        assertTrue(s.contains("version: 3"), "a re-written document must carry the current version, yaml:\n" + s);
    }

    @Test
    public void test_roundTrip_customEventWithMultiLineYamlParams() {
        DispatcherEventYaml p = customEvent("""
                snapshotId: 17
                projectCode: DRONE
                tags:
                  - a
                  - b
                """);

        DispatcherEventYaml restored = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(p));

        assertEquals(p, restored);
    }

    @Test
    public void test_roundTrip_customEventWithJsonParams() {
        DispatcherEventYaml p = customEvent("{\"snapshotId\":17,\"name\":\"\u03A9: \\\"quoted\\\"\"}");

        DispatcherEventYaml restored = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(p));

        assertEquals(p, restored);
    }

    private static DispatcherEventYaml customEvent(String params) {
        DispatcherEventYaml p = new DispatcherEventYaml();
        p.createdOn = "22/09/2026 10:11:12";
        p.event = "RG_CUSTOM_EVENT";
        p.contextId = "ctx-1";
        p.params = params;
        return p;
    }
}
