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

package aiai.api.v1.launchpad;

import aiai.api.v1.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class Process {

    public String name;
    public String code;
    public EnumsApi.ProcessType type;
    public boolean collectResources = false;
    public List<String> snippetCodes;
    public String preSnippetCode;
    public String postSnippetCode;
    public boolean parallelExec;

    /**
     * Timeout before terminating a process with snippet
     * value in seconds
     * null or 0 mean the infinite execution
     */
    public Long timeoutBeforeTerminate;

    @Deprecated
    public String inputType;
    public String inputResourceCode;
    public String outputType;
    public String outputStorageUrl;
    public String outputResourceCode;
    public List<Meta> metas = new ArrayList<>();
    public int order;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meta {
        public String key;
        public String value;
        public String ext;
    }

    public Meta getMeta(String key) {
        if (metas==null) {
            return null;
        }
        for (Meta meta : metas) {
            if (meta.key.equals(key)) {
                return meta;
            }
        }
        return null;
    }
}
