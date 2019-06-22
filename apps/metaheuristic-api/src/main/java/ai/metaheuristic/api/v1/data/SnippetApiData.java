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

package ai.metaheuristic.api.v1.data;

import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.sourcing.GitInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 5/10/2019
 * Time: 2:14 AM
 */
public class SnippetApiData {

    public static final String META_TASK_PARAMS_VERSION = "task-params-version";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString(exclude = {"console"})
    public static class SnippetExecResult {
        public String snippetCode;
        public boolean isOk;
        public int exitCode;
        public String console;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetExec {
        public SnippetExecResult exec = new SnippetExecResult();
        public List<SnippetExecResult> preExecs;
        public List<SnippetExecResult> postExecs;
        public SnippetExecResult generalExec;

        public boolean allSnippetsAreOk() {
            if (exec==null || !exec.isOk) {
                return false;
            }
            if (generalExec!=null && !generalExec.isOk) {
                return false;
            }
            if (preExecs!=null) {
                for (SnippetExecResult preExec : preExecs) {
                    if (!preExec.isOk) {
                        return false;
                    }
                }
            }
            if (postExecs!=null) {
                for (SnippetExecResult postExec : postExecs) {
                    if (!postExec.isOk) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetConfigStatus {
        public boolean isOk;
        public String error;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetConfig {

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
        public Map<EnumsApi.Type, String> checksumMap;
        public SnippetInfo info = new SnippetInfo();
        public String checksum;
        public GitInfo git;
        public boolean skipParams = false;
        public List<Meta> metas = new ArrayList<>();

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

        public int getTaskParamsVersion() {
            final Meta meta = getMeta(META_TASK_PARAMS_VERSION);
            return (meta!=null) ? Integer.valueOf(meta.value) : 1;
        }
    }
}
