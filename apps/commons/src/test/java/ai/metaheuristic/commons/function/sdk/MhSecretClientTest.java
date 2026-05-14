/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.commons.function.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Round-trip tests for {@link MhSecretClient}. Uses a hand-rolled fake
 * server that mirrors the Processor's secret-channel protocol: accepts
 * the connection, reads the framed check-code, sends the framed key.
 *
 * <p>{@code @Execution(SAME_THREAD)} — real loopback sockets, serial to
 * avoid port-allocation contention.
 *
 * @author Sergio Lissner
 */
@Execution(SAME_THREAD)
public class MhSecretClientTest {

    /** Builds a fake Processor that expects a specific check-code and replies with a specific key. */
    private static Thread fakeProcessor(
            ServerSocket server,
            String expectedCheckCode,
            byte[] keyToSend,
            AtomicReference<String> receivedCheckCode,
            AtomicReference<Throwable> err) {
        return Thread.ofVirtual().start(() -> {
            try (Socket peer = server.accept()) {
                peer.setSoTimeout(2_000);
                DataInputStream in = new DataInputStream(peer.getInputStream());
                int len = in.readInt();
                byte[] code = new byte[len];
                in.readFully(code);
                receivedCheckCode.set(new String(code, StandardCharsets.UTF_8));

                DataOutputStream out = new DataOutputStream(peer.getOutputStream());
                out.writeInt(keyToSend.length);
                out.write(keyToSend);
                out.flush();
            } catch (Throwable t) {
                err.set(t);
            }
        });
    }

    @Test
    public void test_exchange_sendsCheckCode_andReceivesKey() throws Exception {
        byte[] expectedKey = "sk-test-key-PPPP".getBytes(StandardCharsets.UTF_8);
        String checkCode = "check-code-AAAA-BBBB";

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5_000);
            int port = server.getLocalPort();

            AtomicReference<String> receivedCheckCode = new AtomicReference<>();
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            Thread serverThread = fakeProcessor(server, checkCode, expectedKey, receivedCheckCode, serverErr);

            byte[] actualKey = MhSecretClient.exchange(port, checkCode, 5_000);
            serverThread.join(5_000);

            assertNull(serverErr.get(), "fake processor should not have errored");
            assertEquals(checkCode, receivedCheckCode.get(),
                    "fake processor must have received the client's check-code");
            assertArrayEquals(expectedKey, actualKey,
                    "client must have received the exact key bytes");
        }
    }

    @Test
    public void test_exchange_throwsIO_whenKeyFrameTooLarge() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5_000);
            int port = server.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket peer = server.accept()) {
                    DataInputStream in = new DataInputStream(peer.getInputStream());
                    int len = in.readInt();
                    in.readFully(new byte[len]);
                    // Reply with an oversized length prefix (no payload follows; we won't reach reading it).
                    DataOutputStream out = new DataOutputStream(peer.getOutputStream());
                    out.writeInt(MhSecretClient.MAX_KEY_FRAME_BYTES + 1);
                    out.flush();
                } catch (IOException ignored) {
                    // expected: client will close
                }
            });

            IOException ex = assertThrows(IOException.class,
                    () -> MhSecretClient.exchange(port, "anycode", 5_000));
            assertTrue(ex.getMessage().contains("0667.020"));
        }
    }

    @Test
    public void test_openFromArgv_returnsNull_whenArgvEmpty() throws Exception {
        assertNull(MhSecretClient.openFromArgv(new String[0]));
    }

    @Test
    public void test_openFromArgv_returnsNull_whenLastArgIsNotAFile() throws Exception {
        assertNull(MhSecretClient.openFromArgv(new String[]{"--first", "/definitely/not/a/real/path/abcxyz.yaml"}));
    }
}
