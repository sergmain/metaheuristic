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
public class TaskParamsYamlV2 implements BaseParams {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetInfoV2 {
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
    public static class SnippetConfigV2 implements Cloneable {

        @SneakyThrows
        public SnippetConfigV2 clone() {
            final SnippetConfigV2 clone = (SnippetConfigV2) super.clone();
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
        public SnippetInfoV2 info = new SnippetInfoV2();
        public String checksum;
        public GitInfo git;
        public boolean skipParams = false;
        public List<Meta> metas = new ArrayList<>();

        // this field is here only for compatibility
        public Integer version;
    }

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class TaskYamlV2 {
        public Map<String, List<String>> inputResourceCodes = new HashMap<>();
        public SnippetConfigV2 snippet;
        public List<SnippetConfigV2> preSnippets;
        public List<SnippetConfigV2> postSnippets;
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
    }

    public final int version = 2;
    public TaskYamlV2 taskYaml = new TaskYamlV2();

}
