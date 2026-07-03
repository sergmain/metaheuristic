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

package ai.metaheuristic.commons.yaml.license;

import ai.metaheuristic.api.data.license.LicenseConfigYaml;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

/**
 * Registry for the license-config version chain. Callers use BASE_YAML_UTILS.to(yaml) (returns the
 * version-less LicenseConfigYaml, upgrading through the chain) and BASE_YAML_UTILS.toString(cfg).
 *
 * @author Serge
 */
public class LicenseConfigYamlUtils {

    private static final LicenseConfigYamlUtilsV1 YAML_UTILS_V_1 = new LicenseConfigYamlUtilsV1();
    private static final LicenseConfigYamlUtilsV1 DEFAULT_UTILS = YAML_UTILS_V_1;

    public static final BaseYamlUtils<LicenseConfigYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1
            ),
            DEFAULT_UTILS
    );
}
