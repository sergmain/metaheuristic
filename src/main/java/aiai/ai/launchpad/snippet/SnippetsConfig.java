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
package aiai.ai.launchpad.snippet;

import lombok.Data;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.List;

@Data
public class SnippetsConfig {

    public enum SnippetType {fit, predict, custom}

    @Data
    public static class SnippetConfig {
/*
- snippet:
        ns: aiai.fit.default.snippet
        type: fit
        file: fit-model.py
        version: 1.0
*/
        public String name;
        public SnippetType type;
        public String file;
        public String version;

    }

    public List<SnippetConfig> snippets;


    public static SnippetsConfig loadSnippetYaml(InputStream is) {

        Yaml yaml = new Yaml(new Constructor(SnippetsConfig.class));

        SnippetsConfig config = yaml.load(is);
        return config;
    }
}
