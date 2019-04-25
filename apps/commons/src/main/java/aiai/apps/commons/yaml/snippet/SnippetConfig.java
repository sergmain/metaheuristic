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

import aiai.api.v1.EnumsApi;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SnippetConfig {

    private static final SnippetConfigStatus SNIPPET_CONFIG_STATUS_OK = new SnippetConfigStatus(true, null);

    @Data
    public static class SnippetInfo {
        public boolean signed;
        /**
         * snippet's binary length
         */
        public long length;
    }

    @Data
    public static class GitInfo {
        public String repo;
        // right now it'll be always as origin
//        public String remote;
        public String branch;
        public String commit;
    }

    /**
     * code of snippet, i.e. simple-app:1.0
     */
    public String code;
    public String type;
    public String file;
    /**
     * params for command line fo invoking snippet
     */
    public String params;
    public String env;
    public EnumsApi.SnippetSourcing sourcing;
    public GitInfo git;
    public boolean metrics = false;
    public Map<Checksum.Type, String> checksumMap;
    public SnippetInfo info = new SnippetInfo();
    public String checksum;

    public SnippetConfigStatus validate() {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(type) || StringUtils.isBlank(env)) {
            return new SnippetConfigStatus(false, "A field is null or empty: " + this.toString());
        }
        if (!StrUtils.isSnippetCodeOk(code)) {
            return new SnippetConfigStatus(false, "Snippet code has wrong chars: "+code+", allowed only: " + StrUtils.ALLOWED_CHARS_SNIPPET_CODE_REGEXP);
        }
        switch (sourcing) {
            case launchpad:
                if (StringUtils.isBlank(file)) {
                    return new SnippetConfigStatus(false, "sourcing is 'launchpad' but file is empty: " + this.toString());
                }
                break;
            case station:
                if (StringUtils.isNoneBlank(file)) {
                    return new SnippetConfigStatus(false, "sourcing is 'system', but file is not empty: " + this.toString());
                }
                break;
            case git:
                break;
        }
        return SNIPPET_CONFIG_STATUS_OK;
    }
}
