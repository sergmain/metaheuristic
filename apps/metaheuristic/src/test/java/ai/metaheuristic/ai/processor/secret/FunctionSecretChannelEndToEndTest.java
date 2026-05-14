/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import ai.metaheuristic.commons.function.sdk.MhSecretClient;
import ai.metaheuristic.commons.function.sdk.MhSecretHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * End-to-end test for Stage 6 + Stage 7: the real Processor-side
 * {@link FunctionSecretChannel} talks to the real Function-side
 * {@link MhSecretClient}. Exercises the full handshake protocol in-process.
 *
 * @author Sergio Lissner
 */
@Execution(SAME_THREAD)
public class FunctionSecretChannelEndToEndTest {

    @Test
    public void test_endToEnd_processorChannelTalksToClientSdk_andDeliversKey() throws Exception {
        String checkCode = "e2e-check-code-DDDD-EEEE";
        byte[] keyBytes = "sk-end-to-end-MMMM-NNNN".getBytes(StandardCharsets.UTF_8);

        try (FunctionSecretChannel channel = new FunctionSecretChannel(5_000)) {
            int port = channel.getPort();

            // Run the Processor-side handoff in one vthread; the client side runs
            // on the main thread.
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            Thread server = Thread.ofVirtual().start(() -> {
                try {
                    channel.handoff(checkCode, keyBytes);
                } catch (Throwable t) {
                    serverErr.set(t);
                }
            });

            // Client side: use MhSecretClient.exchange (low-level, doesn't need a params file).
            byte[] received = MhSecretClient.exchange(port, checkCode, 5_000);
            server.join(5_000);

            assertNull(serverErr.get(), "Processor channel must not have errored");
            assertArrayEquals(keyBytes, received,
                    "Client SDK must receive the exact key bytes the Processor sent");
        }
    }

    @Test
    public void test_endToEnd_wrongCheckCode_clientGetsClosedSocket_noKeyLeaks() throws Exception {
        String expectedCheckCode = "real-check-code";
        String attackerCheckCode = "wrong-check-code!";
        byte[] keyBytes = "sk-never-leak".getBytes(StandardCharsets.UTF_8);

        try (FunctionSecretChannel channel = new FunctionSecretChannel(5_000)) {
            int port = channel.getPort();

            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            Thread server = Thread.ofVirtual().start(() -> {
                try {
                    channel.handoff(expectedCheckCode, keyBytes);
                } catch (Throwable t) {
                    // Expected: SecurityException on mismatch
                    serverErr.set(t);
                }
            });

            // Client sends the wrong code. The Processor should throw and close
            // without writing the key; the client's readInt() therefore hits
            // EOF and DataInputStream throws.
            try {
                byte[] received = MhSecretClient.exchange(port, attackerCheckCode, 5_000);
                // If we got here, something went very wrong: the key leaked.
                fail("Client must NOT receive the key on mismatch; got " + received.length + " bytes");
            } catch (java.io.IOException expected) {
                // good: socket was closed before the key could be read
            }
            server.join(5_000);

            assertNotNull(serverErr.get(), "Processor must have thrown on check-code mismatch");
            assertTrue(serverErr.get() instanceof SecurityException,
                    "Processor must have thrown SecurityException, got " + serverErr.get().getClass());
        }
    }

    @Test
    public void test_endToEnd_clientHandleZerosBufferOnClose() throws Exception {
        String checkCode = "buf-zero-check";
        byte[] keyBytes = "sk-zeroable".getBytes(StandardCharsets.UTF_8);

        try (FunctionSecretChannel channel = new FunctionSecretChannel(5_000)) {
            int port = channel.getPort();

            Thread.ofVirtual().start(() -> {
                try {
                    channel.handoff(checkCode, keyBytes);
                } catch (Throwable ignored) {
                }
            });

            byte[] received = MhSecretClient.exchange(port, checkCode, 5_000);
            try (MhSecretHandle handle = new MhSecretHandle(received)) {
                assertEquals("sk-zeroable", handle.asString());
            }
            // After try-with-resources: buffer must be zeroed.
            for (byte b : received) {
                assertEquals(0, b, "byte must be zeroed after handle close");
            }
        }
    }
}
