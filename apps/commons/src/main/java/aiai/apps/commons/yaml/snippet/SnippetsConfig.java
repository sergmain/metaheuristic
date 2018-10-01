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

import aiai.apps.commons.utils.Checksum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Data
public class SnippetsConfig {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetConfigStatus {
        public boolean isOk;
        public String error;
    }

    public static final SnippetConfigStatus SNIPPET_CONFIG_STATUS_OK = new SnippetConfigStatus(true, null);

    @Data
    @ToString
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
        public String env;
        public Map<Checksum.Type, String> checksums;

        public SnippetConfigStatus verify() {
            if (StringUtils.isBlank(name) || type==null || StringUtils.isBlank(file) || StringUtils.isBlank(version) || StringUtils.isBlank(env)) {
                return new SnippetConfigStatus(false, "A field is null or empty: " + this.toString());
            }
            if (name.indexOf(':')!=-1 || version.indexOf(':')!=-1) {
                return new SnippetConfigStatus(false, "Fields 'name' and 'version' can't contain ':'" + this.toString());
            }
            return SNIPPET_CONFIG_STATUS_OK;
        }
    }

    public List<SnippetConfig> snippets;

}
