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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Static guard over the SourceCode yaml test resources under {@code src/test/resources/source_code}
 * (read from the classpath copy in {@code target/test-classes/source_code}).
 *
 * <p>Every test SourceCode must have a globally-unique {@code source.uid}: the V3 shared-DB harness
 * builds source-code infra once, keyed by uid, so two resources sharing a uid would collide on the
 * single shared H2 DB and make tests steal each other's source code.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class TestSourceCodeUidUniqueness {

    private static final String SOURCE_CODE_DIR = "/source_code";

    // relative file name -> parsed SourceCode
    private static Map<String, SourceCodeParamsYaml> loadAllSourceCodes() {
        final URL url = TestSourceCodeUidUniqueness.class.getResource(SOURCE_CODE_DIR);
        assertNotNull(url, "SourceCode resource dir not found on classpath: " + SOURCE_CODE_DIR);

        final Path root;
        try {
            root = Paths.get(url.toURI());
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(TestSourceCodeUidUniqueness::isYaml)
                    .sorted()
                    .collect(Collectors.toMap(
                            p -> root.relativize(p).toString().replace('\\', '/'),
                            TestSourceCodeUidUniqueness::parse,
                            (a, b) -> { throw new IllegalStateException("duplicate file key"); },
                            LinkedHashMap::new));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isYaml(Path p) {
        final String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".yaml") || n.endsWith(".yml");
    }

    private static SourceCodeParamsYaml parse(Path p) {
        try {
            return SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(Files.readString(p));
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to parse SourceCode yaml: " + p, e);
        }
    }

    @Test
    public void allYamls_parseToSourceCode_withNonBlankUid() {
        final Map<String, SourceCodeParamsYaml> all = loadAllSourceCodes();
        assertFalse(all.isEmpty(), "No SourceCode yaml files were found on the classpath under " + SOURCE_CODE_DIR);
        all.forEach((file, scpy) -> {
            assertNotNull(scpy.source, "source is null in " + file);
            assertNotNull(scpy.source.uid, "source.uid is null in " + file);
            assertFalse(scpy.source.uid.isBlank(), "source.uid is blank in " + file);
        });
    }

    @Test
    public void noTwoYamlsShareTheSameUid() {
        final Map<String, SourceCodeParamsYaml> all = loadAllSourceCodes();

        // uid -> files that declare it
        final Map<String, List<String>> byUid = new LinkedHashMap<>();
        all.forEach((file, scpy) ->
                byUid.computeIfAbsent(scpy.source.uid, k -> new ArrayList<>()).add(file));

        final String duplicates = byUid.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "  uid '" + e.getKey() + "' is used by " + e.getValue())
                .collect(Collectors.joining("\n"));

        assertTrue(duplicates.isEmpty(), "Duplicate SourceCode uid(s) across test resources:\n" + duplicates);
    }
}
