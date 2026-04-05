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

package ai.metaheuristic.commons.graph.source_code_graph;

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.BundleProcessingException;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 4/2026
 */
class MhscIncludeResolverTest {

    private static Function<String, String> mapResolver(Map<String, String> parts) {
        return name -> {
            String content = parts.get(name);
            if (content == null) {
                throw new BundleProcessingException("564.500 Include file not found: " + name);
            }
            return content;
        };
    }

    @Test
    void test_noIncludes_returnsOriginal() {
        String content = """
                source "test-1.0" (strict) {
                    def x = "1"
                    my-process := internal mh.nop { timeout 10 }
                }
                """;

        String result = MhscIncludeResolver.resolve(content, name -> {
            throw new IllegalStateException("Should not be called");
        });

        assertEquals(content, result);
    }

    @Test
    void test_hasIncludes_detectsInclude() {
        assertTrue(MhscIncludeResolver.hasIncludes("    include \"my-part\"\n"));
        assertFalse(MhscIncludeResolver.hasIncludes("// include \"commented-out\"\n    my-process := internal mh.nop {}"));
    }

    @Test
    void test_simpleInclude_substitutesContent() {
        String content = """
                source "test-1.0" (strict) {
                    def call_cc_ver = "1.0.4"
                    include "shared-body"
                }
                """;

        String partContent = """
                variables {
                    <- projectCode
                }

                mh.evaluation := internal mh.evaluation {
                    -> synthetic: ext=".txt"
                    meta expression = "synthetic = true"
                }""";

        String result = MhscIncludeResolver.resolve(content, mapResolver(Map.of("shared-body", partContent)));

        // The include line should be replaced with the part content
        assertFalse(result.contains("include \"shared-body\""));
        assertTrue(result.contains("variables {"));
        assertTrue(result.contains("mh.evaluation"));
        assertTrue(result.contains("def call_cc_ver"));
    }

    @Test
    void test_indentPreservation() {
        String content = "source \"test\" {\n    include \"part1\"\n}\n";
        String partContent = "line1\nline2\nline3";

        String result = MhscIncludeResolver.resolve(content, mapResolver(Map.of("part1", partContent)));

        // Each non-empty line of the part should be indented with 4 spaces
        assertTrue(result.contains("    line1\n"));
        assertTrue(result.contains("    line2\n"));
        assertTrue(result.contains("    line3"));
    }

    @Test
    void test_multipleIncludes() {
        String content = """
                source "test" {
                    include "header"
                    my-process := internal mh.nop { timeout 10 }
                    include "footer"
                }
                """;

        Map<String, String> parts = Map.of(
                "header", "variables {\n    <- projectCode\n}",
                "footer", "mh.finish := internal mh.finish { timeout 60 }"
        );

        String result = MhscIncludeResolver.resolve(content, mapResolver(parts));

        assertFalse(result.contains("include"));
        assertTrue(result.contains("variables"));
        assertTrue(result.contains("my-process"));
        assertTrue(result.contains("mh.finish"));
    }

    @Test
    void test_nestedIncludes() {
        String content = "source \"test\" {\n    include \"outer\"\n}\n";
        String outerPart = "// outer start\ninclude \"inner\"\n// outer end";
        String innerPart = "mh.nop := internal mh.nop { timeout 10 }";

        Map<String, String> parts = Map.of("outer", outerPart, "inner", innerPart);
        String result = MhscIncludeResolver.resolve(content, mapResolver(parts));

        assertFalse(result.contains("include"));
        assertTrue(result.contains("// outer start"));
        assertTrue(result.contains("mh.nop"));
        assertTrue(result.contains("// outer end"));
    }

    @Test
    void test_circularInclude_throws() {
        String content = "include \"a\"";
        Map<String, String> parts = Map.of("a", "include \"b\"", "b", "include \"a\"");

        BundleProcessingException ex = assertThrows(BundleProcessingException.class,
                () -> MhscIncludeResolver.resolve(content, mapResolver(parts)));
        assertTrue(ex.message.contains("Circular include"));
    }

    @Test
    void test_missingInclude_throws() {
        String content = "include \"nonexistent\"";

        BundleProcessingException ex = assertThrows(BundleProcessingException.class,
                () -> MhscIncludeResolver.resolve(content, mapResolver(Map.of())));
        assertTrue(ex.message.contains("not found"));
    }

    @Test
    void test_includeInComment_notResolved() {
        // A line comment should not trigger include resolution
        String content = "// include \"should-not-resolve\"\nmy-process := internal mh.nop {}";

        String result = MhscIncludeResolver.resolve(content, name -> {
            throw new IllegalStateException("Should not be called for commented includes");
        });

        assertEquals(content, result);
    }

    @Test
    void test_endToEnd_parseable() {
        // Verify that after include resolution, the result parses into a valid SourceCodeGraph
        String content = """
                source "include-test-1.0" (strict) {
                    def call_cc_ver = "1.0.4"
                    include "test-body"
                }
                """;

        String partContent = """
                variables {
                    <- projectCode
                }

                mh.evaluation := internal mh.evaluation {
                    -> synthetic: ext=".txt"
                    meta expression = "synthetic = true"
                }

                my-process := mhdg-rg.call-cc-${call_cc_ver} {
                    name "Test process"
                    <- synthetic, projectCode
                    -> output: type=result, ext=".jsonl"
                    timeout 120
                }""";

        String resolved = MhscIncludeResolver.resolve(content, mapResolver(Map.of("test-body", partContent)));

        // Parse the resolved content — should not throw
        ai.metaheuristic.api.data.SourceCodeGraph graph =
                SourceCodeGraphFactory.parse(ai.metaheuristic.api.EnumsApi.SourceCodeLang.mhsc, resolved);

        assertEquals("include-test-1.0", graph.uid);
        // Should have 3 processes: evaluation, my-process, auto-generated mh.finish
        assertEquals(3, graph.processes.size());
    }

    @Test
    void test_emptyLinesInPart_preservedCorrectly() {
        String content = "source \"test\" {\n    include \"part\"\n}\n";
        String partContent = "line1\n\nline3";

        String result = MhscIncludeResolver.resolve(content, mapResolver(Map.of("part", partContent)));

        // Empty lines should remain empty (not get indentation added)
        assertTrue(result.contains("    line1\n\n    line3"));
    }

    @Test
    void test_mhscpExtension_notMatchedByMhscSuffixFilter() {
        // .mhscp files must NOT be matched by the .mhsc suffix filter used in BundleUtils.YAML_SUFFIX_FILTER
        // This ensures part files are excluded from SourceCode processing
        SuffixFileFilter filter = new SuffixFileFilter(CommonConsts.YAML_EXT, CommonConsts.YML_EXT, CommonConsts.MHSC_EXT);

        assertTrue(filter.accept(new File("test.mhsc")), ".mhsc should match");
        assertTrue(filter.accept(new File("test.yaml")), ".yaml should match");
        assertTrue(filter.accept(new File("test.yml")), ".yml should match");
        assertFalse(filter.accept(new File("test.mhscp")), ".mhscp must NOT match .mhsc filter");
        assertFalse(filter.accept(new File("rg-shared-body.mhscp")), ".mhscp part file must NOT match");
    }
}
