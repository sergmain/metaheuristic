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

package ai.metaheuristic.commons.function.sdk;

import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import org.jspecify.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reference Java client for the Function secret-handoff protocol.
 *
 * <p>Canonical usage in a Java Function:
 * <pre>{@code
 * public static void main(String[] args) throws Exception {
 *     try (MhSecretHandle secret = MhSecretClient.openFromArgv(args)) {
 *         if (secret == null) {
 *             // No secret was provisioned for this task; either we don't
 *             // need one, or the Function should exit non-zero.
 *             return;
 *         }
 *         String apiKey = secret.asString();  // brief lifetime
 *         // ... use apiKey ...
 *     } // secret.close() zeros the buffer
 * }
 * }</pre>
 *
 * <p>The contract: the LAST positional argument in {@code args} is the
 * absolute path to the TaskFileParamsYaml file. The client reads
 * {@code task.secretPort} and {@code task.checkCode} from that file (NOT
 * from {@code args} flags), connects to {@code 127.0.0.1:secretPort},
 * sends a framed check-code, reads a framed key, returns the bytes.
 *
 * <p>See {@code VAULT-SECRET-HANDOFF-PROTOCOL.md} for the wire-format spec.
 *
 * @author Sergio Lissner
 */
public final class MhSecretClient {

    private MhSecretClient() {}

    /** Hard cap on the key frame the Processor is allowed to send. */
    public static final int MAX_KEY_FRAME_BYTES = 65_536;

    /** Connect timeout for the loopback handshake (ms). */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5_000;

    /**
     * Reads the TaskFileParamsYaml referenced by {@code argv[-1]} and, if
     * both {@code task.secretPort} and {@code task.checkCode} are present,
     * performs the loopback handshake and returns a closeable handle on the
     * decrypted key bytes.
     *
     * <p>Returns {@code null} if no handoff is configured for this task
     * (either field absent in the params YAML, or {@code argv} is empty).
     */
    public static @Nullable MhSecretHandle openFromArgv(String[] argv) throws IOException {
        if (argv == null || argv.length == 0) {
            return null;
        }
        // Hard contract (TaskProcessor.java): last positional arg is the
        // absolute path to the params YAML file.
        Path paramsPath = Paths.get(argv[argv.length - 1]);
        if (!Files.isRegularFile(paramsPath)) {
            return null;
        }
        return openFromParamsFile(paramsPath);
    }

    /**
     * Variant for callers that already know the params-file path (e.g.
     * tests or wrappers that parse argv themselves).
     */
    public static @Nullable MhSecretHandle openFromParamsFile(Path paramsPath) throws IOException {
        String yaml = Files.readString(paramsPath, StandardCharsets.UTF_8);
        TaskFileParamsYaml params = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        if (params == null || params.task == null) {
            return null;
        }
        Integer port = params.task.secretPort;
        String checkCode = params.task.checkCode;
        if (port == null || checkCode == null || checkCode.isBlank()) {
            return null;
        }
        byte[] key = exchange(port, checkCode, DEFAULT_CONNECT_TIMEOUT_MS);
        return new MhSecretHandle(key);
    }

    /**
     * Low-level: perform one handshake against a known {@code (port, checkCode)}
     * pair. Returns the key bytes. Caller owns and should zero them.
     *
     * <p>Wire steps:
     * <ol>
     *   <li>Connect to {@code 127.0.0.1:port} with the given timeout.</li>
     *   <li>Send {@code [4-byte BE length][checkCode bytes UTF-8]}.</li>
     *   <li>Read {@code [4-byte BE length][key bytes]}.</li>
     *   <li>Close.</li>
     * </ol>
     */
    public static byte[] exchange(int port, String checkCode, int connectTimeoutMs) throws IOException {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress("127.0.0.1", port), connectTimeoutMs);
            sock.setSoTimeout(connectTimeoutMs);

            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            byte[] codeBytes = checkCode.getBytes(StandardCharsets.UTF_8);
            out.writeInt(codeBytes.length);
            out.write(codeBytes);
            out.flush();

            DataInputStream in = new DataInputStream(sock.getInputStream());
            int len = in.readInt();
            if (len < 0 || len > MAX_KEY_FRAME_BYTES) {
                throw new IOException("0667.020 invalid key frame length: " + len);
            }
            byte[] key = new byte[len];
            in.readFully(key);
            return key;
        }
    }
}
