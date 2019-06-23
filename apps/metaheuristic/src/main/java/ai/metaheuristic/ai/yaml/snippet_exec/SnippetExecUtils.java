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
package ai.metaheuristic.ai.yaml.snippet_exec;

import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class SnippetExecUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(SnippetApiData.SnippetExec.class);
    }

    public static String toString(SnippetApiData.SnippetExec config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static SnippetApiData.SnippetExec to(String s) {
        return (SnippetApiData.SnippetExec) YamlUtils.to(s, getYaml());
    }

    public static SnippetApiData.SnippetExec to(InputStream is) {
        return (SnippetApiData.SnippetExec) YamlUtils.to(is, getYaml());
    }

    public static SnippetApiData.SnippetExec to(File file) {
        return (SnippetApiData.SnippetExec) YamlUtils.to(file, getYaml());
    }
}
