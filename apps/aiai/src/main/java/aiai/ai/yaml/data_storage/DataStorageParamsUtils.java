/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml.data_storage;

import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import aiai.apps.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
public class DataStorageParamsUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(DataStorageParams.class);
    }

    public static String toString(DataStorageParams config) {
        return YamlUtils.toString(config, yaml);
    }

    public static DataStorageParams to(String s) {
        return (DataStorageParams) YamlUtils.to(s, yaml);
    }

    public static DataStorageParams to(InputStream is) {
        return (DataStorageParams) YamlUtils.to(is, yaml);
    }

    public static DataStorageParams to(File file) {
        return (DataStorageParams) YamlUtils.to(file, yaml);
    }

}