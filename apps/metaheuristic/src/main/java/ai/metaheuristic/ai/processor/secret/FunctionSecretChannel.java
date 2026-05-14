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

package ai.metaheuristic.ai.processor.secret;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Per-launch loopback handoff channel. Opens a {@link ServerSocket} bound to
 * {@code 127.0.0.1} on an OS-assigned ephemeral port; performs a single-shot
 * check-code challenge handshake with the spawned Function; releases the
 * decrypted API key only if the challenge passes.
 *
 * <h3>Protocol</h3>
 * <pre>
 *   Processor: accept() with SO_TIMEOUT
 *   Function:  send  [4-byte BE len][checkCode bytes]
 *   Processor: read frame; constant-time compare to expected checkCode
 *              - mismatch → SecurityException (caller kills Function)
 *              - match    → continue
 *   Processor: send  [4-byte BE len][keyBytes]
 *   Processor: close
 * </pre>
 *
 * <h3>Race defense</h3>
 * <p>The check-code is a fresh random token written ONLY into the per-task
 * params file ({@code TaskFileParamsYaml.Task.checkCode}). A racing local
 * attacker who connects first cannot produce the correct token because
 * they never read that file. The mismatch path tears down the connection
 * and aborts before any key bytes leave the Processor.
 *
 * <h3>Single ownership</h3>
 * <p>Caller owns {@code keyBytes} and {@code expectedCheckCode} and must zero
 * both after this channel returns (success or failure). The channel never
 * retains a reference to either outside the synchronous {@link #handoff} call.
 *
 * <h3>Error prefix</h3>
 * <p>{@code 667.}.
 *
 * @author Sergio Lissner
 */
@Slf4j
public class FunctionSecretChannel implements AutoCloseable {

    /** Default accept() timeout. The Function should connect within milliseconds of spawn. */
    public static final int DEFAULT_ACCEPT_TIMEOUT_MS = 10_000;

    /** Hard cap on inbound frame length, defends against a malicious peer sending a huge size prefix. */
    public static final int MAX_INBOUND_FRAME_BYTES = 4096;

    private final ServerSocket serverSocket;
    private final int port;

    public FunctionSecretChannel() throws IOException {
        this(DEFAULT_ACCEPT_TIMEOUT_MS);
    }

    public FunctionSecretChannel(int acceptTimeoutMs) throws IOException {
        this.serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        this.serverSocket.setSoTimeout(acceptTimeoutMs);
        this.port = serverSocket.getLocalPort();
        log.debug("667.005 FunctionSecretChannel bound to 127.0.0.1:{}", port);
    }

    public int getPort() {
        return port;
    }

    /**
     * Waits for the Function to connect, performs the check-code challenge,
     * writes the framed key, closes the socket. Synchronous.
     *
     * @param expectedCheckCode the token the Processor wrote into the Function's params file
     * @param keyBytes the decrypted API key
     * @throws SecurityException     if the challenge token doesn't match
     * @throws java.net.SocketTimeoutException if no peer connects within the timeout
     * @throws IOException           on any other I/O failure
     */
    public void handoff(String expectedCheckCode, byte[] keyBytes) throws IOException {
        try (Socket peer = serverSocket.accept()) {
            log.debug("667.010 FunctionSecretChannel peer connected on port {}", port);
            challengeAndWrite(peer.getInputStream(), peer.getOutputStream(), expectedCheckCode, keyBytes);
        }
    }

    /**
     * Pure protocol step, extracted so tests can drive it with in-memory
     * streams without opening real sockets.
     */
    static void challengeAndWrite(InputStream in, OutputStream out, String expectedCheckCode, byte[] keyBytes) throws IOException {
        byte[] expectedBytes = expectedCheckCode.getBytes(StandardCharsets.UTF_8);
        byte[] received = readFrame(in, MAX_INBOUND_FRAME_BYTES);

        if (!constantTimeEquals(received, expectedBytes)) {
            log.warn("667.020 check-code mismatch: peer sent {} bytes, expected {}",
                    received.length, expectedBytes.length);
            throw new SecurityException("667.021 check-code mismatch on secret-channel handshake");
        }

        writeFrame(out, keyBytes);
        out.flush();
        log.debug("667.030 secret handoff complete ({} key bytes written)", keyBytes.length);
    }

    /**
     * Reads {@code [4-byte BE length][payload]} from {@code in}, returning the payload.
     * Caps length at {@code maxLen} to defend against a malicious peer.
     */
    static byte[] readFrame(InputStream in, int maxLen) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int len = dis.readInt();
        if (len < 0 || len > maxLen) {
            throw new IOException("667.040 inbound frame length out of range: " + len + " (max " + maxLen + ")");
        }
        byte[] buf = new byte[len];
        dis.readFully(buf);
        return buf;
    }

    /** Writes {@code [4-byte BE length][payload]} to {@code out}. */
    static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        ByteBuffer hdr = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        hdr.putInt(payload.length);
        out.write(hdr.array());
        out.write(payload);
    }

    /**
     * Length-independent constant-time byte comparison. Returns true iff the
     * arrays have the same length AND content; does NOT short-circuit on
     * first difference, so a timing observer learns nothing about how much
     * of the prefix matched.
     */
    static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            // Still consume the same amount of work to defeat length-leak timing,
            // but the result is correct either way.
            int diff = a.length ^ b.length;
            int maxLen = Math.max(a.length, b.length);
            for (int i = 0; i < maxLen; i++) {
                diff |= (i < a.length ? a[i] : 0) ^ (i < b.length ? b[i] : 0);
            }
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }
}
