/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Serge
 * Date: 9/9/2020
 * Time: 5:02 AM
 */
public class TestYamlSchemeValidator {

    private static final String SEE_MORE_INFO = "https://docs.metaheuristic.ai";

    @Test
    public void testOneElement() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
            List.of(new Scheme(
                List.of(
                    new Element("root1", true, false, new String[]{"element1", "element2"}),
                    new Element("root2", true, false, new String[]{"element3"})
                ),1, SEE_MORE_INFO, true)
            ),
            "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg= """
           root1:
             element1: code
             element2: env
           root2:
             element3: env
           version: 1
           """;
        assertNull(validator.validateStructureOfDispatcherYaml(cfg), "!!! RN this test isn't working because YamlSchemeValidator doesn't support yaml with more than one element at root");
    }


    @Test
    public void testOk() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root", true, false, new String[]{"element1", "element2"})
                        ), 1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml",(es)-> es, SEE_MORE_INFO
        );

        String cfg="root:\n  - element1: 1\n    element2: 2";
        assertNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testOk_2() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root1", true, false, new String[]{"element1-1", "element1-2"}),
                                new Element("root2", true, false, new String[]{"element2-1", "element2-2"})
                        ),1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg="root1:\n  - element1-1: 1\n    element1-2: 2\nroot2:\n  - element2-1: 1\n    element2-2: 2";
        assertNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testOk_3() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root1", true, false, new String[]{"element1-1", "element1-2"}),
                                new Element("root2", false, false, new String[]{"element2-1", "element2-2"})
                        ),1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg="root1:\n  - element1-1: 1\n    element1-2: 2";
        assertNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testOkWithVersion() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root", true, false, new String[]{"element1", "element2"})
                        ),1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg="root:\n  - element1: 1\n    element2: 2\nversion: 1";
        assertNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testWrongVersion() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root", true, false, new String[]{"element1", "element2"})
                        ),1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg= """
                root:
                  - element1: 1
                    element2: 2
                version: 99""";
        assertNotNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testRootElement() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root", true, false, new String[]{"element1", "element2"})
                        ),1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg="root1:\n  - element1: 1\n    element2: 2";
        assertNotNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void test2ndLevelElements() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> (
                List.of(new Scheme(
                        List.of(
                                new Element("root", true, false, new String[]{"element1", "element2"})
                        ),1, SEE_MORE_INFO, true)
                ),
                "the config file test.yaml", (es)-> es, SEE_MORE_INFO
        );

        String cfg="root1:\n  - element11: 1\n    element2: 2";
        final String errors = validator.validateStructureOfDispatcherYaml(cfg);
        System.out.println(errors);
        assertNotNull(errors);
    }
}
