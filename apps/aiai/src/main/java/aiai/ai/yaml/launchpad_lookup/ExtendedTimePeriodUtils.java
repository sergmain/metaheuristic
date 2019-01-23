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

package aiai.ai.yaml.launchpad_lookup;

import aiai.ai.yaml.env.EnvYaml;
import aiai.apps.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

public class ExtendedTimePeriodUtils {
    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(ExtendedTimePeriod.class);
    }

    public static String toString(ExtendedTimePeriod config) {
        return YamlUtils.toString(config, yaml);
    }

    public static ExtendedTimePeriod to(String s) {
        return (ExtendedTimePeriod) YamlUtils.to(s, yaml);
    }

    public static ExtendedTimePeriod to(InputStream is) {
        return (ExtendedTimePeriod) YamlUtils.to(is, yaml);
    }

    public static ExtendedTimePeriod to(File file) {
        return (ExtendedTimePeriod) YamlUtils.to(file, yaml);
    }

}
