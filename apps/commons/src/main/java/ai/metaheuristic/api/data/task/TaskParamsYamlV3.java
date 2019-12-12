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

package ai.metaheuristic.api.data.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@Data
public class TaskParamsYamlV3 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetInfoV3 {
        public boolean signed;
        /**
         * snippet's binary length
         */
        public long length;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class SnippetConfigV3 implements Cloneable {

        @SneakyThrows
        public SnippetConfigV3 clone() {
            final SnippetConfigV3 clone = (SnippetConfigV3) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            if (this.metas != null) {
                clone.metas = new ArrayList<>();
                for (Meta meta : this.metas) {
                    clone.metas.add(new Meta(meta.key, meta.value, meta.ext));
                }
            }
            return clone;
        }

        /**
         * code of snippet, i.e. simple-app:1.0
         */
        public String code;
        public String type;
        public String file;
        /**
         * params for command line fo invoking snippet
         * <p>
         * this isn't a holder for yaml-based config
         */
        public String params;
        public String env;
        public EnumsApi.SnippetSourcing sourcing;
        public boolean metrics = false;
        public Map<EnumsApi.Type, String> checksumMap;
        public SnippetInfoV3 info = new SnippetInfoV3();
        public String checksum;
        public GitInfo git;
        public boolean skipParams = false;
        public List<Meta> metas = new ArrayList<>();
    }

    @Data
    public static class TaskYamlV3 {
        public Map<String, List<String>> inputResourceCodes = new HashMap<>();
        public SnippetConfigV3 snippet;
        public List<SnippetConfigV3> preSnippets;
        public List<SnippetConfigV3> postSnippets;
        public Map<String, String> hyperParams;
        public String outputResourceCode;
        public Map<String, DataStorageParams> resourceStorageUrls = new HashMap<>();
        public boolean clean = false;

        /**
         * Timeout before terminate a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;

        // fields which are initialized at station
        public String workingPath;

        // TODO this isn't good solution because it doesn't support ftp, hadoop or something else
        // TODO as a result we'll support only direct access to files
        public Map<String, List<String>> inputResourceAbsolutePaths = new HashMap<>();
        public String outputResourceAbsolutePath;

        // key - resource code, value - real file name of resource
        public Map<String, String> realNames;
    }

    public final int version = 3;
    public TaskYamlV3 taskYaml = new TaskYamlV3();

}
