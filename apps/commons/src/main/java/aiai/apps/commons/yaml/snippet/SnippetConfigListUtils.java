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
package aiai.apps.commons.yaml.snippet;

import aiai.apps.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class SnippetConfigListUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(SnippetConfigList.class);
    }

    public static String toString(SnippetConfigList config) {
        return YamlUtils.toString(config, yaml);
    }

    public static SnippetConfigList to(String s) {
        return (SnippetConfigList) YamlUtils.to(s, yaml);
    }

    public static SnippetConfigList to(InputStream is) {
        return (SnippetConfigList) YamlUtils.to(is, yaml);
    }

    public static SnippetConfigList to(File file) {
        return (SnippetConfigList) YamlUtils.to(file, yaml);
    }
}
