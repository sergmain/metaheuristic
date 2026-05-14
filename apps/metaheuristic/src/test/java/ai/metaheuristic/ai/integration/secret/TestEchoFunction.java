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

import ai.metaheuristic.commons.function.sdk.MhSecretClient;
import ai.metaheuristic.commons.function.sdk.MhSecretHandle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;

/**
 * Minimal Function used as a child JVM by {@link VaultSecretHandoffWireE2ETest}.
 *
 * <p>Canonical Stage-7 SDK usage. Reads the params YAML at {@code argv[-1]}
 * (per the hard contract documented in TaskProcessor), performs the
 * loopback handshake, writes the hex-encoded key bytes to the file given
 * by {@code --out-file=...} so the parent test can assert what the
 * Function actually received.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>{@code 0} — key received and written.</li>
 *   <li>{@code 1} — params YAML had no secret fields populated; wrote
 *       sentinel "NO_SECRET" to {@code --out-file} so the parent test
 *       can disambiguate from a generic crash.</li>
 *   <li>{@code 2} — bad invocation (no {@code --out-file=}).</li>
 *   <li>non-zero from JVM — uncaught exception; the parent test prints
 *       captured stdout/stderr to make the failure debuggable.</li>
 * </ul>
 *
 * <p>This class is deliberately small. Anything beyond reading the
 * secret + writing the hex is a distraction from what Stage 8 is testing.
 *
 * @author Sergio Lissner
 */
public class TestEchoFunction {

    public static void main(String[] args) throws Exception {
        String outPath = null;
        for (String a : args) {
            if (a.startsWith("--out-file=")) {
                outPath = a.substring("--out-file=".length());
                break;
            }
        }
        if (outPath == null) {
            System.err.println("TestEchoFunction: missing --out-file=<path> argument");
            System.exit(2);
        }

        try (MhSecretHandle handle = MhSecretClient.openFromArgv(args)) {
            if (handle == null) {
                Files.writeString(Paths.get(outPath), "NO_SECRET", StandardCharsets.UTF_8);
                System.exit(1);
            }
            String hex = HexFormat.of().formatHex(handle.bytes());
            Files.writeString(Paths.get(outPath), hex, StandardCharsets.UTF_8);
        }
        // Implicit System.exit(0) — JVM exits 0 on normal main() return.
    }
}
