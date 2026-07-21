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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 'description' source-option field on SourceCodeGraph, parsed from the MHSC format.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SourceCodeGraphDescriptionFieldTest {

    @Test
    public void test_mhsc_with_description() {
        String mhsc = """
                source "desc-test" (strict, type = "mhdg-rg.requirement", description = "This is description") {
                    p1 := some-func {
                        <- in1
                        -> out1
                    }
                }
                """;
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertNotNull(scg);
        assertEquals("desc-test", scg.uid);
        assertEquals("This is description", scg.description);
        assertEquals("mhdg-rg.requirement", scg.type);
    }

    @Test
    public void test_mhsc_without_description() {
        String mhsc = """
                source "no-desc-test" (type = "mhdg-rg.requirement") {
                    p1 := some-func {
                        <- in1
                        -> out1
                    }
                }
                """;
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertNotNull(scg);
        assertEquals("no-desc-test", scg.uid);
        assertNull(scg.description);
    }

    @Test
    public void test_mhsc_description_as_only_option() {
        String mhsc = """
                source "only-desc" (description = "solo") {
                    p1 := some-func {
                        <- in1
                        -> out1
                    }
                }
                """;
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertNotNull(scg);
        assertEquals("only-desc", scg.uid);
        assertEquals("solo", scg.description);
    }

    @Test
    public void test_mhsc_description_still_usable_as_identifier() {
        // Adding 'description' to sourceOption must NOT reserve the word globally: it must
        // remain usable as an identifier (here as a process meta key), parity with 'type'.
        String mhsc = """
                source "desc-as-id" {
                    p1 := some-func {
                        <- in1
                        -> out1
                        meta description = "a meta value"
                    }
                }
                """;
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertNotNull(scg);
        assertEquals("desc-as-id", scg.uid);
        // A meta key literally named 'description' is NOT the source-option field.
        assertNull(scg.description);
    }
}
