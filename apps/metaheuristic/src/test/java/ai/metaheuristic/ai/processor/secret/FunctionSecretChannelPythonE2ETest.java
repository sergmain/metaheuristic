/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Cross-language end-to-end test: real Processor-side {@link
 * FunctionSecretChannel} talks to the real Python-side
 * {@code mh_secret_client.exchange()} via a subprocess. Proves the wire
 * protocol is bit-exact across the two reference SDKs.
 *
 * <p><strong>Why this test is {@code @Disabled} by default.</strong>
 * It depends on infrastructure that isn't guaranteed in the standard
 * Maven CI run:
 * <ul>
 *   <li>{@code python3} executable on PATH (Linux/macOS) or {@code python}
 *       on Windows.</li>
 *   <li>PyYAML installed in the resolved Python interpreter (the
 *       {@code mh_secret_client} module imports it at module-load time
 *       even though this specific test path doesn't read a YAML file).</li>
 *   <li>The {@code java/call-cc-func/python/} source tree present
 *       relative to the working directory — assumed at
 *       {@code ../../../call-cc-func/python} when run from
 *       {@code apps/metaheuristic/}.</li>
 * </ul>
 * Enable by removing {@code @Disabled} when running locally on an
 * environment that satisfies the above. CI executes the Java↔Java
 * equivalent in {@link FunctionSecretChannelEndToEndTest} which has no
 * such dependencies.
 *
 * @author Sergio Lissner
 */
@Disabled("Requires python3 + PyYAML + call-cc-func/python/ source tree on disk. " +
          "Cross-language wire-protocol confidence is established by both sides " +
          "passing their respective fake-server tests against the same documented " +
          "protocol; this test is a stronger but infrastructure-dependent proof. " +
          "VERIFIED GREEN on Ubuntu/Python 3.13.7/PyYAML 6.0.2 at Stage 7 commit time.")
@Execution(SAME_THREAD)
public class FunctionSecretChannelPythonE2ETest {

    @Test
    public void test_pythonClient_overRealProcessorChannel_receivesExactKey() throws Exception {
        String checkCode = "py-e2e-check-code-FFFF-GGGG";
        byte[] keyBytes = "sk-py-cross-lang-HHHH-IIII".getBytes(StandardCharsets.UTF_8);

        // Resolve the Python SDK directory. Working dir during
        // `mvn -pl apps/metaheuristic test` is apps/metaheuristic.
        Path pythonSdkDir = Paths.get("..", "..", "..", "call-cc-func", "python")
                .toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(pythonSdkDir),
                "Python SDK dir must exist at " + pythonSdkDir);
        assertTrue(Files.isRegularFile(pythonSdkDir.resolve("mh_secret_client.py")),
                "mh_secret_client.py must exist in " + pythonSdkDir);

        // Temp file the Python subprocess will write the received key into.
        Path outFile = Files.createTempFile("mh-py-e2e-", ".bin");
        outFile.toFile().deleteOnExit();

        try (FunctionSecretChannel channel = new FunctionSecretChannel(15_000)) {
            int port = channel.getPort();

            // Start Processor-side handoff on a virtual thread.
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            Thread server = Thread.ofVirtual().start(() -> {
                try {
                    channel.handoff(checkCode, keyBytes);
                } catch (Throwable t) {
                    serverErr.set(t);
                }
            });

            // Python one-liner: import the SDK, call exchange(), write the result bytes
            // to outFile. Stays inside one process; stdlib + PyYAML only.
            String pythonScript =
                    "import sys; sys.path.insert(0, r'" + pythonSdkDir.toString().replace("'", "\\'") + "');" +
                    "import mh_secret_client as m;" +
                    "data = m.exchange(" + port + ", '" + checkCode + "');" +
                    "open(r'" + outFile.toString().replace("'", "\\'") + "', 'wb').write(bytes(data))";

            String pythonExe = resolvePythonExecutable();
            ProcessBuilder pb = new ProcessBuilder(pythonExe, "-c", pythonScript)
                    .redirectErrorStream(true);
            Process proc = pb.start();
            boolean finished = proc.waitFor(15, TimeUnit.SECONDS);
            byte[] childOutput = proc.getInputStream().readAllBytes();
            assertTrue(finished, "Python subprocess timed out; output=" + new String(childOutput));
            assertEquals(0, proc.exitValue(),
                    "Python subprocess must exit 0; output=" + new String(childOutput));

            server.join(15_000);
            assertNull(serverErr.get(), "Processor channel must not have errored");

            byte[] receivedByPython = Files.readAllBytes(outFile);
            assertArrayEquals(keyBytes, receivedByPython,
                    "Python client must have received the exact key bytes the Processor sent");
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    /**
     * Resolve a usable Python interpreter. Prefers {@code python3}
     * (Linux/macOS canonical), falls back to {@code python} (Windows).
     */
    private static String resolvePythonExecutable() throws IOException, InterruptedException {
        for (String candidate : new String[]{"python3", "python"}) {
            try {
                Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException ignored) {
                // try next
            }
        }
        throw new IOException("No usable Python interpreter on PATH (tried python3, python)");
    }
}
