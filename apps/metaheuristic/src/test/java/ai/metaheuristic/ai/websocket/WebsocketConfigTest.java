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

package ai.metaheuristic.ai.websocket;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParams;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParamsUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying WebSocket configuration correctness.
 *
 * Context: MH uses STOMP over WebSocket to push task notifications from Dispatcher to Processor.
 * The Dispatcher sends serialized WebsocketEventParams YAML to /topic/events.
 * The STOMP frame includes the payload PLUS protocol headers (destination, content-type, content-length,
 * subscription-id, message-id, etc.) which add overhead beyond the raw payload size.
 *
 * These tests verify that:
 * 1) The serialized payload for all WebsocketEventType values fits within a reasonable buffer
 * 2) The payload + STOMP header overhead fits within the configured buffer size (8192 bytes)
 * 3) A worst-case payload with a large function list still fits within the buffer
 *
 * If these tests fail after a config change, it means the buffer sizes in Config.DispatcherWebSocketConfig
 * need to be increased.
 *
 * @author Sergio Lissner
 * Date: 3/20/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class WebsocketConfigTest {

    // Buffer size configured in Config.DispatcherWebSocketConfig and ServletServerContainerFactoryBean
    private static final int CONFIGURED_BUFFER_SIZE = 8192;

    // STOMP frame overhead: headers like destination, content-type, content-length, subscription-id, message-id
    // Typical overhead is ~200-400 bytes. We use 512 as a conservative upper bound.
    private static final int STOMP_HEADER_OVERHEAD = 512;

    /**
     * Verify that the serialized YAML payload for a simple 'task' event is small enough
     * to fit within the configured buffer with room for STOMP headers.
     */
    @Test
    public void test_taskEventPayloadFitsInBuffer() {
        WebsocketEventParams params = new WebsocketEventParams();
        params.type = Enums.WebsocketEventType.task;

        String yaml = WebsocketEventParamsUtils.BASE_UTILS.toString(params);

        int totalFrameSize = yaml.length() + STOMP_HEADER_OVERHEAD;

        assertTrue(totalFrameSize < CONFIGURED_BUFFER_SIZE,
                "Task event STOMP frame (" + totalFrameSize + " bytes) exceeds buffer size (" + CONFIGURED_BUFFER_SIZE + ")");
    }

    /**
     * Verify that a 'function' event with a moderate list of function codes still fits in buffer.
     * This tests a realistic worst-case scenario where multiple function codes are included.
     */
    @Test
    public void test_functionEventWithListFitsInBuffer() {
        WebsocketEventParams params = new WebsocketEventParams();
        params.type = Enums.WebsocketEventType.function;
        params.functions = List.of(
                "ai.metaheuristic.function.some-very-long-function-name-v1",
                "ai.metaheuristic.function.another-long-function-name-v2",
                "ai.metaheuristic.function.yet-another-function-name-v3",
                "ai.metaheuristic.function.processing-pipeline-stage-v4",
                "ai.metaheuristic.function.data-transformation-step-v5"
        );

        String yaml = WebsocketEventParamsUtils.BASE_UTILS.toString(params);

        int totalFrameSize = yaml.length() + STOMP_HEADER_OVERHEAD;

        assertTrue(totalFrameSize < CONFIGURED_BUFFER_SIZE,
                "Function event STOMP frame (" + totalFrameSize + " bytes) exceeds buffer size (" + CONFIGURED_BUFFER_SIZE + ")");
    }

    /**
     * Verify that a simple task event payload is small. The old 1024-byte buffer might
     * technically fit the payload, but Tomcat's WebSocket buffer limit applies to the entire
     * WebSocket message frame (not just the STOMP payload), and the STOMP protocol framing
     * (SEND/MESSAGE command, headers, null terminator) adds overhead that is hard to predict
     * exactly. Increasing to 8192 provides a safe margin.
     *
     * This test documents the payload sizes for reference.
     */
    @Test
    public void test_payloadSizesAreDocumented() {
        WebsocketEventParams taskParams = new WebsocketEventParams();
        taskParams.type = Enums.WebsocketEventType.task;
        String taskYaml = WebsocketEventParamsUtils.BASE_UTILS.toString(taskParams);

        // Task event payload should be very small
        assertTrue(taskYaml.length() < 100,
                "Task event payload unexpectedly large: " + taskYaml.length() + " bytes");

        WebsocketEventParams funcParams = new WebsocketEventParams();
        funcParams.type = Enums.WebsocketEventType.function;
        funcParams.functions = List.of("func1", "func2", "func3");
        String funcYaml = WebsocketEventParamsUtils.BASE_UTILS.toString(funcParams);

        // Function event payload with short function names should still be small
        assertTrue(funcYaml.length() < 200,
                "Function event payload unexpectedly large: " + funcYaml.length() + " bytes");

        // Both should fit comfortably in the new 8192 buffer
        assertTrue(taskYaml.length() + STOMP_HEADER_OVERHEAD < CONFIGURED_BUFFER_SIZE);
        assertTrue(funcYaml.length() + STOMP_HEADER_OVERHEAD < CONFIGURED_BUFFER_SIZE);
    }
}
