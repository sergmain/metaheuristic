/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Serge
 * Date: 9/9/2020
 * Time: 5:02 AM
 */
public class TestYamlSchemeValidator {

    public static final String SEE_MORE_INFO = "https://docs.metaheuristic.ai";

    @Test
    public void testOk() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> ("root", List.of("element1", "element2"), List.of(),
                SEE_MORE_INFO, List.of("1"),"the config file test.yaml",(es)-> es);

        String cfg="root:\n  - element1: 1\n    element2: 2";
        assertNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testOkWithVersion() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> ("root", List.of("element1", "element2"), List.of(),
                SEE_MORE_INFO, List.of("1"),"the config file test.yaml",(es)-> es);

        String cfg="root:\n  - element1: 1\n    element2: 2\nversion: 1";
        assertNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testWrongVersion() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> ("root", List.of("element1", "element2"), List.of(),
                SEE_MORE_INFO, List.of("1"),"the config file test.yaml",(es)-> es);

        String cfg="root:\n  - element1: 1\n    element2: 2\nversion: 2";
        assertNotNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void testRootElement() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> ("root", List.of("element1", "element2"), List.of(),
                SEE_MORE_INFO, List.of("1"),"the config file test.yaml",(es)-> es);

        String cfg="root1:\n  - element1: 1\n    element2: 2";
        assertNotNull(validator.validateStructureOfDispatcherYaml(cfg));
    }

    @Test
    public void test2ndLevelElements() {
        YamlSchemeValidator<String> validator = new YamlSchemeValidator<> ("root", List.of("element1", "element2"), List.of(),
                SEE_MORE_INFO, List.of("1"),"the config file test.yaml",(es)-> es);

        String cfg="root1:\n  - element11: 1\n    element2: 2";
        assertNotNull(validator.validateStructureOfDispatcherYaml(cfg));
    }
}
