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
import aiai.api.v1.sourcing.GitInfo;
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
    public boolean metrics = false;
    public Map<Checksum.Type, String> checksumMap;
    public SnippetInfo info = new SnippetInfo();
    public String checksum;
    public GitInfo git;
    public boolean skipParams = false;

    public SnippetConfigStatus validate() {
        if (StringUtils.isBlank(file) && StringUtils.isBlank(env)) {
            return new SnippetConfigStatus(false, "#401.10 Fields 'file' and 'env' can't be null or empty both.");
        }
        if (StringUtils.isBlank(code) || StringUtils.isBlank(type)) {
            return new SnippetConfigStatus(false, "#401.15 A field is null or empty: " + this.toString());
        }
        if (!StrUtils.isSnippetCodeOk(code)) {
            return new SnippetConfigStatus(false, "#401.20 Snippet code has wrong chars: "+code+", allowed only: " + StrUtils.ALLOWED_CHARS_SNIPPET_CODE_REGEXP);
        }
        if (sourcing==null) {
            return new SnippetConfigStatus(false, "#401.25 Field 'sourcing' is absent");
        }
        switch (sourcing) {
            case launchpad:
                if (StringUtils.isBlank(file)) {
                    return new SnippetConfigStatus(false, "#401.30 sourcing is 'launchpad' but file is empty: " + this.toString());
                }
                break;
            case station:
                break;
            case git:
                if (git==null) {
                    return new SnippetConfigStatus(false, "#401.42 sourcing is 'git', but git info is absent");
                }
                break;
        }
        return SNIPPET_CONFIG_STATUS_OK;
    }
}
