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

package ai.metaheuristic.ai.integration.secret;

import ai.metaheuristic.ai.processor.secret.FunctionSecretChannel;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Stage 8 — End-to-end wire-level integration test for the vault
 * secret-handoff feature (Scope A per the rewritten plan).
 *
 * <p>What this test exercises end-to-end:
 * <ol>
 *   <li>Real {@link FunctionSecretChannel} bound on 127.0.0.1:0.</li>
 *   <li>Real {@link TaskFileParamsYaml} serialized to disk with
 *       {@code task.checkCode} and {@code task.secretPort} populated.</li>
 *   <li>Real child JVM running {@link TestEchoFunction}, which uses the
 *       real {@code MhSecretClient} SDK (Stage 7) to read the params
 *       file, perform the framed handshake, and write the hex of the
 *       received key bytes to a designated output file.</li>
 * </ol>
 *
 * <p>What this test asserts:
 * <ol>
 *   <li>The Function received the EXACT key bytes the Processor sent.</li>
 *   <li>The secret is NOT in the child's cmdline (captured pre-spawn).</li>
 *   <li>The secret is NOT in the child's environment (captured pre-spawn).</li>
 *   <li>The secret is NOT in the params YAML on disk (only checkCode and
 *       secretPort are written there; the key never is).</li>
 *   <li>The secret is NOT in the child's stdout/stderr capture.</li>
 * </ol>
 *
 * <p>Scope-A boundary: this test does NOT stand up Spring, the
 * Dispatcher, the SealedSecretService, the SealedSecretCache, or the
 * full TaskProcessor. Those are covered by isolated unit tests from
 * Stages 1-6 + Stage 7's wire-level e2e tests. Stage 8 covers the one
 * pair-wise interaction that was previously uncovered: real
 * Processor-side channel ↔ real child-JVM Function-side SDK across a
 * real TCP loopback socket and a real params YAML on disk.
 *
 * @author Sergio Lissner
 */
@Execution(SAME_THREAD)
public class VaultSecretHandoffWireE2ETest {

    /** Sentinel string — chosen so any accidental leak shows up in failure messages. */
    private static final String SECRET_VALUE = "sk-test-INTEG-9z-SENTINEL";

    /** Channel + child JVM startup must comfortably finish in this window. */
    private static final int CHILD_JVM_TIMEOUT_SECONDS = 30;
    private static final int CHANNEL_TIMEOUT_MS = 15_000;

    @Test
    public void test_endToEnd_secretReachesFunction_neverLeaksOnWire() throws Exception {
        Path tempDir = Files.createTempDirectory("vault-handoff-e2e-");
        try {
            byte[] keyBytes = SECRET_VALUE.getBytes(StandardCharsets.UTF_8);
            String checkCode = "e2e-check-code-" + UUID.randomUUID();

            // === ARRANGE: real channel + real params YAML on disk ===
            try (FunctionSecretChannel channel = new FunctionSecretChannel(CHANNEL_TIMEOUT_MS)) {
                int port = channel.getPort();

                Path paramsFile = tempDir.resolve("params.yaml");
                writeParamsYamlWithSecretFields(paramsFile, checkCode, port, tempDir);

                Path hexOutFile = tempDir.resolve("out.hex");

                // Processor-side handoff on a virtual thread.
                AtomicReference<Throwable> serverErr = new AtomicReference<>();
                Thread server = Thread.ofVirtual().start(() -> {
                    try {
                        channel.handoff(checkCode, keyBytes);
                    } catch (Throwable t) {
                        serverErr.set(t);
                    }
                });

                // === ACT: spawn TestEchoFunction as a child JVM ===
                ProcessBuilder pb = buildChildJvm(
                        TestEchoFunction.class,
                        List.of("--out-file=" + hexOutFile.toAbsolutePath(),
                                paramsFile.toAbsolutePath().toString()));
                // Capture cmd + env AS THEY WILL BE PASSED to the child — for the
                // wire-leak assertions below. These captures happen BEFORE start()
                // so we are auditing exactly what the child will see at exec time.
                List<String> capturedCmd = List.copyOf(pb.command());
                Map<String, String> capturedEnv = Map.copyOf(pb.environment());

                pb.redirectErrorStream(true);
                Process child = pb.start();
                byte[] childStdoutStderr = child.getInputStream().readAllBytes();
                boolean finished = child.waitFor(CHILD_JVM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                server.join(CHANNEL_TIMEOUT_MS);

                // === ASSERT: child completed successfully ===
                String childOutput = new String(childStdoutStderr, StandardCharsets.UTF_8);
                assertTrue(finished, "Child JVM did not terminate within "
                        + CHILD_JVM_TIMEOUT_SECONDS + "s; output=\n" + childOutput);
                assertEquals(0, child.exitValue(),
                        "Child JVM must exit 0; output=\n" + childOutput);
                assertNull(serverErr.get(),
                        "Processor-side handoff must not have errored: " + serverErr.get());

                // === ASSERT: child received the exact key bytes ===
                String receivedHex = Files.readString(hexOutFile, StandardCharsets.UTF_8).trim();
                String expectedHex = HexFormat.of().formatHex(keyBytes);
                assertEquals(expectedHex, receivedHex,
                        "Function did not receive the correct secret bytes");

                // === ASSERT: secret is NOT in cmdline ===
                for (String arg : capturedCmd) {
                    assertFalse(arg.contains(SECRET_VALUE),
                            "Secret leaked into child cmdline arg: " + arg);
                }

                // === ASSERT: secret is NOT in child's env ===
                capturedEnv.forEach((k, v) -> {
                    assertFalse(v.contains(SECRET_VALUE),
                            "Secret leaked into env var " + k + "=" + v);
                });

                // === ASSERT: secret is NOT in the params YAML on disk ===
                String paramsYamlOnDisk = Files.readString(paramsFile, StandardCharsets.UTF_8);
                assertFalse(paramsYamlOnDisk.contains(SECRET_VALUE),
                        "Secret leaked into params YAML on disk:\n" + paramsYamlOnDisk);
                // Sanity: checkCode and port ARE in the YAML (they are not secret).
                assertTrue(paramsYamlOnDisk.contains(checkCode),
                        "checkCode must be in params YAML; actual:\n" + paramsYamlOnDisk);
                assertTrue(paramsYamlOnDisk.contains(String.valueOf(port)),
                        "secretPort must be in params YAML; actual:\n" + paramsYamlOnDisk);

                // === ASSERT: secret is NOT in the child's stdout/stderr capture ===
                assertFalse(childOutput.contains(SECRET_VALUE),
                        "Secret leaked into child stdout/stderr:\n" + childOutput);
            }
        } finally {
            deleteRecursive(tempDir);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Writes a minimal {@link TaskFileParamsYaml} to disk with the secret
     * fields ({@code checkCode}, {@code secretPort}) populated. Other
     * fields are set to defaults sufficient for round-trip serialization.
     */
    private static void writeParamsYamlWithSecretFields(
            Path file, String checkCode, int secretPort, Path workingDir) throws IOException {
        TaskFileParamsYaml params = new TaskFileParamsYaml();
        params.task.execContextId = 1L;
        params.task.workingPath = workingDir.toAbsolutePath().toString();
        params.task.checkCode = checkCode;
        params.task.secretPort = secretPort;
        // inputs, outputs, metas already initialized to empty lists by the bean.
        String yaml = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(params);
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    /**
     * Build a {@link ProcessBuilder} that will launch {@code mainClass} in
     * a fresh child JVM with the same classpath as the parent test JVM.
     * The parent test JVM's classpath includes both production and test
     * classes (per surefire), so the child can load
     * {@link TestEchoFunction} and the {@code MhSecretClient} SDK.
     */
    private static ProcessBuilder buildChildJvm(Class<?> mainClass, List<String> args) {
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + "/bin/java");
        String classpath = System.getProperty("java.class.path");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass.getName());
        cmd.addAll(args);
        return new ProcessBuilder(cmd);
    }

    /** Best-effort recursive delete; failures here must not mask the test result. */
    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }
}
