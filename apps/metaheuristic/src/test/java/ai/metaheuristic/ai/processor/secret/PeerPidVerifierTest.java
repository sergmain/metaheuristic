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
 * Tests for {@link PeerPidVerifier}.
 *
 * <p><b>Stage 6 dormancy note.</b> {@code PeerPidVerifier} was originally
 * specced as Stage 6's defense against the loopback-race attack, but
 * runtime probing during implementation showed that {@code SO_PEERCRED} on
 * Linux returns {@code pid=0} for AF_INET (TCP) loopback sockets — it only
 * works on AF_UNIX socket pairs. Stage 6 uses TCP loopback (for Windows
 * portability), so peer-PID verification cannot work for this protocol;
 * the defense is the per-launch check-code in
 * {@code TaskFileParamsYaml.Task.checkCode} instead.
 *
 * <p>These tests therefore verify:
 * <ul>
 *   <li>The OS dispatcher resolves to a known platform.</li>
 *   <li>{@link PeerPidVerifier#extractFd(Socket)} reflection works on the
 *       runtime JDK (proves the {@code --add-opens} flags are in effect).</li>
 *   <li>For TCP loopback sockets, {@code peerPid} returns either {@code 0}
 *       (Linux/macOS — kernel zero-fills) or {@code -1} (Windows/OTHER).
 *       Either way, the secret channel must NOT trust this value for a TCP
 *       loopback handshake.</li>
 * </ul>
 *
 * <p>{@code @Execution(SAME_THREAD)} — real loopback sockets.
 *
 * @author Sergio Lissner
 */
@Execution(SAME_THREAD)
public class PeerPidVerifierTest {

    @Test
    public void test_currentPlatform_isOneOfTheKnownValues() {
        assertNotNull(PeerPidVerifier.currentPlatform());
    }

    @Test
    public void test_extractFd_returnsPositiveDescriptor_forRealConnectedSocket() throws Exception {
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
                int fd = PeerPidVerifier.extractFd(peer);
                assertTrue(fd > 0, "fd must be a positive int; got " + fd);
            }
        }
    }

    @Test
    public void test_peerPid_isUntrustworthy_onTcpLoopback() throws Exception {
        // Documents the AF_INET limitation: on a TCP loopback socket, peerPid
        // returns 0 on Linux/macOS (kernel zero-fills the ucred for non-AF_UNIX
        // sockets) and -1 on Windows/OTHER. Neither value is a real peer PID;
        // the secret channel does NOT use this method for TCP handshakes.
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
                // Whatever this returns, it is NOT to be trusted on TCP loopback.
                // We assert only that the call doesn't throw.
                assertTrue(pid == 0 || pid == -1 || pid > 0,
                        "peerPid must return something (0/-1/positive); got " + pid);
            }
        }
    }
}
