/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.yaml.metadata;

import aiai.apps.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class MetadataUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(Metadata.class);
    }

    public static String toString(Metadata config) {
        return YamlUtils.toString(config, yaml);
    }

    public static Metadata to(String s) {
        return (Metadata) YamlUtils.to(s, yaml);
    }

    public static Metadata to(InputStream is) {
        return (Metadata) YamlUtils.to(is, yaml);
    }

    public static Metadata to(File file) {
        return (Metadata) YamlUtils.to(file, yaml);
    }
}
