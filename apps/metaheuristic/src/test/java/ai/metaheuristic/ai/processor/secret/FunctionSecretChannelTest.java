/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Real-socket round-trip tests for {@link FunctionSecretChannel}.
 *
 * <p>{@code @Execution(SAME_THREAD)} — real loopback sockets; serialise to
 * avoid port-allocation contention and accept-timeout races.
 *
 * @author Sergio Lissner
 */
@Execution(SAME_THREAD)
public class FunctionSecretChannelTest {

    /** Drives the Function side: connect, send framed checkCode, read framed key. */
    private static byte[] runFunctionPeer(int port, String checkCode) throws IOException {
        try (Socket peer = new Socket("127.0.0.1", port)) {
            peer.setSoTimeout(5_000);
            OutputStream out = peer.getOutputStream();
            FunctionSecretChannel.writeFrame(out, checkCode.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = peer.getInputStream();
            return FunctionSecretChannel.readFrame(in, 65_536);
        }
    }

    @Test
    public void test_handoff_roundTrip_correctCheckCode_deliversKey() throws Exception {
        String checkCode = "fresh-random-token-AAAA-BBBB";
        byte[] expectedKey = "sk-real-secret-PPPP-QQQQ".getBytes(StandardCharsets.UTF_8);

        try (FunctionSecretChannel channel = new FunctionSecretChannel(5_000)) {
            int port = channel.getPort();

            AtomicReference<byte[]> received = new AtomicReference<>();
            AtomicReference<Throwable> peerErr = new AtomicReference<>();
            Thread peerThread = Thread.ofVirtual().start(() -> {
                try {
                    received.set(runFunctionPeer(port, checkCode));
                } catch (Throwable t) {
                    peerErr.set(t);
                }
            });

            channel.handoff(checkCode, expectedKey);
            peerThread.join(5_000);

            assertNull(peerErr.get(), "peer should not have errored");
            assertArrayEquals(expectedKey, received.get(),
                    "Function must receive the exact key bytes");
        }
    }

    @Test
    public void test_handoff_wrongCheckCode_throwsSecurityException_keyNeverWritten() throws Exception {
        String expectedCheckCode = "the-correct-token";
        String attackerCheckCode = "the-wrong-token!";
        byte[] keyBytes = "sk-never-leaked".getBytes(StandardCharsets.UTF_8);

        try (FunctionSecretChannel channel = new FunctionSecretChannel(5_000)) {
            int port = channel.getPort();

            AtomicReference<byte[]> received = new AtomicReference<>();
            AtomicReference<Throwable> peerErr = new AtomicReference<>();
            Thread peerThread = Thread.ofVirtual().start(() -> {
                try {
                    received.set(runFunctionPeer(port, attackerCheckCode));
                } catch (Throwable t) {
                    // Expected: peer will fail to read the reply because the
                    // server closes after throwing SecurityException.
                    peerErr.set(t);
                }
            });

            SecurityException ex = assertThrows(SecurityException.class,
                    () -> channel.handoff(expectedCheckCode, keyBytes));
            assertTrue(ex.getMessage().contains("667.021"));

            peerThread.join(5_000);

            // The peer side either got an empty read or an IOException. What
            // it MUST NOT have got is the key bytes.
            byte[] r = received.get();
            assertTrue(r == null || !java.util.Arrays.equals(r, keyBytes),
                    "key bytes must never leak on mismatch");
        }
    }

    @Test
    public void test_handoff_noPeerConnects_throwsSocketTimeoutException() throws Exception {
        // Short timeout so the test doesn't drag.
        try (FunctionSecretChannel channel = new FunctionSecretChannel(500)) {
            assertThrows(SocketTimeoutException.class,
                    () -> channel.handoff("any-token",
                            "any-key".getBytes(StandardCharsets.UTF_8)));
        }
    }
}
