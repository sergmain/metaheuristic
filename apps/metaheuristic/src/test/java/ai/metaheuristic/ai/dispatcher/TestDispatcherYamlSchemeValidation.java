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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.processor.processor_environment.FileDispatcherLookupExtendedParams;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 1/10/2021
 * Time: 10:23 PM
 */
public class TestDispatcherYamlSchemeValidation {

    @Test
    public void test_1_v1() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/dispatcher/dispatchers.yaml", StandardCharsets.UTF_8);

        YamlSchemeValidator<Boolean> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
                FileDispatcherLookupExtendedParams.SCHEMES,
                "url",
                (es)-> false,
                "see more"
        );

        Boolean result = YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result);
    }

    @Test
    public void test_2_v1() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/dispatcher/dispatchers-1-v1.yaml", StandardCharsets.UTF_8);

        YamlSchemeValidator<Boolean> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
                FileDispatcherLookupExtendedParams.SCHEMES,
                "url",
                (es)-> false,
                "see more"
        );

        Boolean result = YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result);
    }

    @Test
    public void test_1_v2() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/dispatcher/dispatchers-v2.yaml", StandardCharsets.UTF_8);

        YamlSchemeValidator<Boolean> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
                FileDispatcherLookupExtendedParams.SCHEMES,
                "url",
                (es)-> false,
                "see more"
        );

        Boolean result = YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result);
    }

    @Test
    public void test_2__with_error_v2() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/dispatcher/dispatchers-with-error-v2.yaml", StandardCharsets.UTF_8);

        YamlSchemeValidator<Boolean> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
                FileDispatcherLookupExtendedParams.SCHEMES,
                "url",
                (es)-> true,
                "see more"
        );

        Boolean result = YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNotNull(result);
        assertTrue(result);
    }
}
