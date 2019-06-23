/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.ai.yaml.data_storage;

import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
public class DataStorageParamsUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(DataStorageParams.class);
    }

    public static String toString(DataStorageParams config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static DataStorageParams to(String s) {
        return (DataStorageParams) YamlUtils.to(s, getYaml());
    }

    public static DataStorageParams to(InputStream is) {
        return (DataStorageParams) YamlUtils.to(is, getYaml());
    }

    public static DataStorageParams to(File file) {
        return (DataStorageParams) YamlUtils.to(file, getYaml());
    }

}