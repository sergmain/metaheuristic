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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Slf4j
public class SnippetCoreUtils {

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

    public static String getDataForChecksumWhenGitSourcing(SnippetApiData.SnippetConfig snippetConfig) {
        return "" + snippetConfig.env+", " + snippetConfig.file +" " + snippetConfig.params;
    }


    public static List<EnumsApi.OS> getSupportedOS(List<Meta> metas) {
        final Meta meta = getMeta(metas, ConstsApi.META_MH_SNIPPET_SUPPORTED_OS);
        if (meta != null && meta.value!=null && !meta.value.isBlank()) {
            try {
                StringTokenizer st = new StringTokenizer(meta.value, ", ");
                List<EnumsApi.OS> oss = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    String s =  st.nextToken();
                    oss.add( EnumsApi.OS.valueOf(s) );
                }
                return oss;
            }
            catch (IllegalArgumentException e) {
                log.error("#309.001 Error parsing metadata with supported OS: " + meta, e);
                return List.of();
            }
        }
        return List.of();
    }

    public static Meta getMeta(List<Meta> metas, String... keys) {
        if (metas==null) {
            return null;
        }
        if (keys==null || keys.length==0) {
            return null;
        }
        for (Meta meta : metas) {
            for (String key : keys) {
                if (meta.key.equals(key)) {
                    return meta;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    public static int getTaskParamsVersion(List<Meta> metas) {
        final Meta meta = getMeta(metas, ConstsApi.META_MH_TASK_PARAMS_VERSION, ConstsApi.META_TASK_PARAMS_VERSION);
        return (meta!=null) ? Integer.parseInt(meta.value) : 1;
    }
}
