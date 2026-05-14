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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Tests for {@link PeerPidVerifier} skeleton (step 2a). The FFM-backed
 * impls (step 2b) are tested by the round-trip secret-channel test.
 *
 * <p>{@code @Execution(SAME_THREAD)} because tests open real loopback sockets
 * and we'd rather not race on port allocation.
 *
 * @author Sergio Lissner
 */
@Execution(SAME_THREAD)
public class PeerPidVerifierTest {

    @Test
    public void test_currentPlatform_isOneOfTheKnownValues() {
        PeerPidVerifier.Platform p = PeerPidVerifier.currentPlatform();
        assertNotNull(p);
        // On the CI Ubuntu runner this is LINUX.
        // Just verify the enum resolved to something concrete.
    }

    @Test
    public void test_extractFd_returnsPositiveDescriptor_forRealConnectedSocket() throws Exception {
        // Open a real loopback server + connect, then ask the verifier for
        // the raw FD. Should succeed and return a non-negative int on any
        // OpenJDK with the @add-opens flags in effect.
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(2_000);
            int port = server.getLocalPort();

            // Connect a client in a virtual thread so server.accept() doesn't deadlock.
            Thread.ofVirtual().start(() -> {
                try (Socket ignored = new Socket("127.0.0.1", port)) {
                    Thread.sleep(200);
                } catch (Exception e) {
                    // swallowed; the test asserts on the server side
                }
            });

            try (Socket peer = server.accept()) {
                int fd = PeerPidVerifier.extractFd(peer);
                assertTrue(fd > 0, "fd must be a positive int; got " + fd);
            }
        }
    }

    @Test
    public void test_peerPid_returnsNegativeOne_inStep2aSkeleton() throws Exception {
        // Step 2a returns -1 from every platform impl (FFM not wired yet).
        // After 2b lands, this test should assert > 0 on Linux/macOS.
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(2_000);
            int port = server.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket ignored = new Socket("127.0.0.1", port)) {
                    Thread.sleep(200);
                } catch (Exception e) {
                    // swallowed
                }
            });

            try (Socket peer = server.accept()) {
                long pid = PeerPidVerifier.peerPid(peer);
                // Step 2a: every platform returns -1. Step 2b will flip this on
                // Linux/macOS — when that lands, update this assertion to assert > 0
                // for those platforms.
                assertEquals(-1L, pid, "Step 2a skeleton returns -1; flip after Step 2b");
            }
        }
    }
}
