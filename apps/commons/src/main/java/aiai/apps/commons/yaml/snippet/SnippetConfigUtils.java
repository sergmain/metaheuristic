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
package aiai.apps.commons.yaml.snippet;

import aiai.api.v1.data.SnippetApiData;
import aiai.apps.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class SnippetConfigUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(SnippetApiData.SnippetConfig.class);
    }

    public static String toString(SnippetApiData.SnippetConfig config) {
        return YamlUtils.toString(config, yaml);
    }

    public static SnippetApiData.SnippetConfig to(String s) {
        return (SnippetApiData.SnippetConfig) YamlUtils.to(s, yaml);
    }

    public static SnippetApiData.SnippetConfig to(InputStream is) {
        return (SnippetApiData.SnippetConfig) YamlUtils.to(is, yaml);
    }

    public static SnippetApiData.SnippetConfig to(File file) {
        return (SnippetApiData.SnippetConfig) YamlUtils.to(file, yaml);
    }
}
