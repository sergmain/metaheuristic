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

package ai.metaheuristic.ai.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DispatcherRequestor.getDispatcherWsUrl() — the real package-private method.
 *
 * Context: The Processor builds a ws:// or wss:// URL from the Dispatcher's http:// or https:// URL by:
 *   1) Appending the WS_DISPATCHER_URL path (/ws/dispatcher)
 *   2) Replacing http:// with ws:// or https:// with wss://
 *
 * The constructed URL determines the Origin header in the WebSocket handshake.
 * If the Dispatcher's STOMP endpoint doesn't allow the processor's origin,
 * the handshake will fail silently. This is why setAllowedOriginPatterns("*")
 * must be configured on the server side.
 *
 * A prior bug mapped both http:// and https:// to ws://, breaking TLS WebSocket connections.
 *
 * @author Sergio Lissner
 * Date: 3/20/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class DispatcherWsUrlTest {

    @Test
    public void test_httpUrlConvertedToWs() {
        var du = new ProcessorAndCoreData.DispatcherUrl("http://localhost:8080");
        String result = DispatcherRequestor.getDispatcherWsUrl(du);
        assertEquals("ws://localhost:8080/ws/dispatcher", result);
    }

    @Test
    public void test_httpsUrlConvertedToWss() {
        var du = new ProcessorAndCoreData.DispatcherUrl("https://dispatcher.example.com");
        String result = DispatcherRequestor.getDispatcherWsUrl(du);
        assertEquals("wss://dispatcher.example.com/ws/dispatcher", result);
    }

    @Test
    public void test_httpUrlWithPort() {
        var du = new ProcessorAndCoreData.DispatcherUrl("http://192.168.1.100:8889");
        String result = DispatcherRequestor.getDispatcherWsUrl(du);
        assertEquals("ws://192.168.1.100:8889/ws/dispatcher", result);
    }

    @Test
    public void test_httpsUrlWithPort() {
        var du = new ProcessorAndCoreData.DispatcherUrl("https://10.0.0.1:8443");
        String result = DispatcherRequestor.getDispatcherWsUrl(du);
        assertEquals("wss://10.0.0.1:8443/ws/dispatcher", result);
    }

    @Test
    public void test_unknownProtocolThrows() {
        var du = new ProcessorAndCoreData.DispatcherUrl("ftp://example.com");
        assertThrows(IllegalStateException.class, () -> DispatcherRequestor.getDispatcherWsUrl(du));
    }
}
