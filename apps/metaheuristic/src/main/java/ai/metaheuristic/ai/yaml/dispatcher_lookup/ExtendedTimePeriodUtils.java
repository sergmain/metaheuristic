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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

public class ExtendedTimePeriodUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(ExtendedTimePeriod.class);
    }

    public static String toString(ExtendedTimePeriod config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static ExtendedTimePeriod to(String s) {
        return (ExtendedTimePeriod) YamlUtils.to(s, getYaml());
    }

    public static ExtendedTimePeriod to(InputStream is) {
        return (ExtendedTimePeriod) YamlUtils.to(is, getYaml());
    }

    public static ExtendedTimePeriod to(File file) {
        return (ExtendedTimePeriod) YamlUtils.to(file, getYaml());
    }

}
