/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
package ai.metaheuristic.commons.yaml.data_storage;

import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.auth.ApiAuth;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtilsV1;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtilsV2;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
public class DataStorageParamsUtils {

    private static final DataStorageParamsUtilsV1 UTILS_V_1 = new DataStorageParamsUtilsV1();
    private static final DataStorageParamsUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    public static final BaseYamlUtils<DataStorageParams> UTILS = new BaseYamlUtils<>(
        Map.of(
            1, UTILS_V_1
        ),
        DEFAULT_UTILS
    );

}