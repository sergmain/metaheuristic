/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package metaheuristic.api.v1.data;

import metaheuristic.api.v1.EnumsApi;
import metaheuristic.api.v1.sourcing.GitInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

/**
 * @author Serge
 * Date: 5/10/2019
 * Time: 2:14 AM
 */
public class SnippetApiData {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetExecResult {
        public boolean isOk;
        public int exitCode;
        public String console;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetExec {
        public SnippetExecResult exec = new SnippetExecResult();
        public SnippetExecResult preExec;
        public SnippetExecResult postExec;
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

    }
}
