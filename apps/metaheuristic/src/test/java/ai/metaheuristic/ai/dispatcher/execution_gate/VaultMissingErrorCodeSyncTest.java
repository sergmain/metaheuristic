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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Keeps the dispatcher execution-gate rule for "Vault has no entry" in lockstep with the production code that
 * emits it. TWO files are read from the repo and compared:
 *
 * <ul>
 *   <li>{@code application.properties} - the analyzer regex the dispatcher matches a failed Task's console against;
 *   <li>{@code DownloadSealedSecretService.java} - the Processor code that actually prints the 01.812.04x line.
 * </ul>
 *
 * <p>The concern is drift. The regex carries an error code; the emission site carries the same code as a string
 * literal. If either moves - the emission code is edited, or the regex is "tidied" - and the other is not, the
 * rule silently stops matching and a locked Vault goes back to burning a Task's tries with nothing to say why.
 * This test fails the build the moment the two disagree.
 *
 * <p>It also pins the RULE-ERROR-CODE-SCHEME position: the emission site has been MIGRATED to {@code 01.812.04x}
 * (MH app segment {@code 01}), and the regex still carries the optional {@code (01\.)?} prefix so it matches the
 * migrated form now AND would still match a legacy {@code 812.04x} - the rule needs no coordinated change either way.
 *
 * <p>Paths are relative to the module dir, which is the working directory Surefire runs in.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class VaultMissingErrorCodeSyncTest {

    private static final Path PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path EMITTER = Path.of(
            "src/main/java/ai/metaheuristic/ai/processor/actors/DownloadSealedSecretService.java");

    private static final String REGEX_KEY = "mh.dispatcher.execution-gate.analyzers[0].regex[0]";

    /** The value of the analyzer regex key, with the properties-file's doubled backslashes un-doubled to a real regex. */
    private static String analyzerRegexFromProperties() throws IOException {
        for (String line : Files.readAllLines(PROPERTIES, StandardCharsets.UTF_8)) {
            final String trimmed = line.strip();
            if (trimmed.startsWith("#") || !trimmed.startsWith(REGEX_KEY)) {
                continue;
            }
            final int eq = trimmed.indexOf('=');
            assertTrue(eq > 0, "malformed property line: " + trimmed);
            // in a .properties value a regex backslash is written doubled; Spring un-doubles it before use
            return trimmed.substring(eq + 1).strip().replace("\\\\", "\\");
        }
        return fail("property '" + REGEX_KEY + "' not found in " + PROPERTIES);
    }

    /** Every "01.812.04x Vault has no entry ..." message literal the emitter actually prints, with its code prefix. */
    private static List<String> vaultMissingMessagesFromEmitter() throws IOException {
        final String source = Files.readString(EMITTER, StandardCharsets.UTF_8);
        // the emitted literal, as passed to String.format - captured up to the first format placeholder
        final Matcher m = Pattern.compile("\"((?:\\d{2}\\.)?\\d{3}\\.\\d{3} Vault has no entry[^\"%]*)")
                .matcher(source);
        final List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    public void test_theEmitterPrintsExactlyTheTwoMigratedVaultMissingCodesTheRegexExpects() throws IOException {
        final List<String> messages = vaultMissingMessagesFromEmitter();

        // if this count changes, the emitter added/removed a Vault-missing branch and the rule must be revisited
        assertEquals(2, messages.size(),
                "expected exactly two 'Vault has no entry' emissions (01.812.040 and 01.812.041), found: " + messages);
        assertTrue(messages.stream().anyMatch(s -> s.startsWith("01.812.040 ")), "01.812.040 emission missing: " + messages);
        assertTrue(messages.stream().anyMatch(s -> s.startsWith("01.812.041 ")), "01.812.041 emission missing: " + messages);
    }

    @Test
    public void test_theConfiguredRegexMatchesEveryVaultMissingLineTheEmitterPrints() throws IOException {
        final Pattern regex = Pattern.compile(analyzerRegexFromProperties());

        for (String message : vaultMissingMessagesFromEmitter()) {
            assertTrue(regex.matcher(message).find(),
                    "the analyzer regex no longer matches an emitted line - the rule would silently stop firing.\n"
                            + "  regex:   " + regex.pattern() + "\n"
                            + "  console: " + message);
        }
    }

    @Test
    public void test_theRegexIsForwardCompatibleWithTheMigratedErrorCode() throws IOException {
        final Pattern regex = Pattern.compile(analyzerRegexFromProperties());

        // legacy (what is emitted today) AND migrated (RULE-ERROR-CODE-SCHEME: MH app segment 01) both match,
        // so migrating DownloadSealedSecretService later needs no change here
        assertTrue(regex.matcher("812.040 Vault has no entry for companyId=2, keyCode=RG_API_AUTH.").find());
        assertTrue(regex.matcher("01.812.040 Vault has no entry for companyId=2, keyCode=RG_API_AUTH.").find());
        // and it does not fire on an unrelated 812.x line
        assertFalse(regex.matcher("812.050 BAD_GATEWAY fetching sealed secret, keyCode=RG_API_AUTH").find());
    }
}
