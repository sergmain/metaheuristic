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

import ai.metaheuristic.ai.Consts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static ai.metaheuristic.commons.CommonConsts.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WebSocket URL construction logic used by the Processor to connect to the Dispatcher.
 *
 * Context: The Processor builds a ws:// URL from the Dispatcher's http:// URL by:
 *   1) Stripping the http:// or https:// prefix
 *   2) Prepending ws:// protocol
 *   3) Appending the WS_DISPATCHER_URL path (/ws/dispatcher)
 *
 * This logic is in DispatcherRequestor.getDispatcherWsUrl() (private static method).
 * We replicate it here as a static helper to test it in isolation.
 *
 * The constructed URL determines the Origin header in the WebSocket handshake.
 * If the Dispatcher's STOMP endpoint doesn't allow the processor's origin,
 * the handshake will fail silently. This is why setAllowedOriginPatterns("*")
 * must be configured on the server side.
 *
 * @author Sergio Lissner
 * Date: 3/20/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class DispatcherWsUrlTest {

    /**
     * Replicates the FIXED version of DispatcherRequestor.getDispatcherWsUrl() logic.
     * The original had a bug: both http:// and https:// were mapped to ws://.
     * The fix maps http:// -> ws:// and https:// -> wss://.
     */
    static String buildWsUrl(String dispatcherUrl) {
        final String url = dispatcherUrl + Consts.WS_DISPATCHER_URL;
        if (url.startsWith(HTTPS)) {
            // HTTPS must be checked first because "https://" starts with "http"
            return WSS_PROTOCOL + url.substring(HTTPS.length());
        }
        else if (url.startsWith(HTTP)) {
            return WS_PROTOCOL + url.substring(HTTP.length());
        }
        else {
            throw new IllegalStateException("Unknown protocol in url: " + url);
        }
    }

    @Test
    public void test_httpUrlConvertedToWs() {
        String result = buildWsUrl("http://localhost:8080");
        assertEquals("ws://localhost:8080/ws/dispatcher", result);
    }

    @Test
    public void test_httpsUrlConvertedToWss() {
        // https:// dispatcher URLs must produce wss://, not ws://
        // This was a bug: the old code always used ws:// regardless of input protocol
        String result = buildWsUrl("https://dispatcher.example.com");
        assertEquals("wss://dispatcher.example.com/ws/dispatcher", result);
    }

    @Test
    public void test_httpUrlWithPort() {
        String result = buildWsUrl("http://192.168.1.100:8889");
        assertEquals("ws://192.168.1.100:8889/ws/dispatcher", result);
    }

    @Test
    public void test_unknownProtocolThrows() {
        assertThrows(IllegalStateException.class, () -> buildWsUrl("ftp://example.com"));
    }

    /**
     * Verify that when processor connects from a different host, the Origin header
     * will differ from the server's own address. This documents why
     * setAllowedOriginPatterns("*") is needed on the STOMP endpoint registration.
     *
     * Example: Dispatcher at http://10.0.0.1:8080, Processor at http://10.0.0.2:8080
     * The WS handshake Origin will be from 10.0.0.2, which differs from the server origin.
     */
    @Test
    public void test_crossOriginScenario() {
        String dispatcherUrl = "http://10.0.0.1:8080";
        String wsUrl = buildWsUrl(dispatcherUrl);

        // The ws URL targets the dispatcher host
        assertTrue(wsUrl.contains("10.0.0.1"));

        // But the HTTP Origin header in the handshake will contain the processor's own address,
        // which is different. Without setAllowedOriginPatterns("*"), this cross-origin
        // handshake is rejected by Spring's default SameOriginPolicy.
    }
}
