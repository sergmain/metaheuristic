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

import metaheuristic.api.v1.data.SnippetApiData;
import aiai.apps.commons.utils.StrUtils;
import org.apache.commons.lang3.StringUtils;

public class SnippetUtils {

    private static final SnippetApiData.SnippetConfigStatus SNIPPET_CONFIG_STATUS_OK = new SnippetApiData.SnippetConfigStatus(true, null);

    public static SnippetApiData.SnippetConfigStatus validate(SnippetApiData.SnippetConfig snippetConfig) {
        if ((snippetConfig.file ==null || snippetConfig.file.isBlank()) && (snippetConfig.env ==null || snippetConfig.env.isBlank())) {
            return new SnippetApiData.SnippetConfigStatus(false, "#401.10 Fields 'file' and 'env' can't be null or empty both.");
        }
        if (snippetConfig.code ==null || snippetConfig.code.isBlank() || snippetConfig.type ==null || snippetConfig.type.isBlank()) {
            return new SnippetApiData.SnippetConfigStatus(false, "#401.15 A field is null or empty: " + snippetConfig.toString());
        }
        if (!StrUtils.isSnippetCodeOk(snippetConfig.code)) {
            return new SnippetApiData.SnippetConfigStatus(false, "#401.20 Snippet code has wrong chars: "+ snippetConfig.code +", allowed only: " + StrUtils.ALLOWED_CHARS_SNIPPET_CODE_REGEXP);
        }
        if (snippetConfig.sourcing ==null) {
            return new SnippetApiData.SnippetConfigStatus(false, "#401.25 Field 'sourcing' is absent");
        }
        switch (snippetConfig.sourcing) {
            case launchpad:
                if (StringUtils.isBlank(snippetConfig.file)) {
                    return new SnippetApiData.SnippetConfigStatus(false, "#401.30 sourcing is 'launchpad' but file is empty: " + snippetConfig.toString());
                }
                break;
            case station:
                break;
            case git:
                if (snippetConfig.git ==null) {
                    return new SnippetApiData.SnippetConfigStatus(false, "#401.42 sourcing is 'git', but git info is absent");
                }
                break;
        }
        return SNIPPET_CONFIG_STATUS_OK;
    }
}
