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
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
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
@EqualsAndHashCode
public class TaskParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (taskYaml.context==null) {
            throw new CheckIntegrityFailedException("snippet exec context is null");
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetInfo {
        public boolean signed;
        /**
         * snippet's binary length
         */
        public long length;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MachineLearning {
        // does this snippet support metrics
        public boolean metrics = false;
        // does this snippet support fitting detection
        public boolean fitting = false;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskMachineLearning {
        public Map<String, String> hyperParams;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class SnippetConfig implements Cloneable {

        @SneakyThrows
        public SnippetConfig clone() {
            final SnippetConfig clone = (SnippetConfig) super.clone();
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
        public Map<EnumsApi.Type, String> checksumMap;
        public SnippetInfo info = new SnippetInfo();
        public String checksum;
        public GitInfo git;
        public boolean skipParams = false;
        public List<Meta> metas = new ArrayList<>();
        public MachineLearning ml;
    }

    @Data
    public static class TaskYaml {
        public SnippetConfig snippet;
        public List<SnippetConfig> preSnippets;
        public List<SnippetConfig> postSnippets;
        public Map<String, List<String>> inputResourceIds = new HashMap<>();
        public Map<String, String> outputResourceIds = new HashMap<>();
        public Map<String, SourceCodeParamsYaml.Variable> resourceStorageUrls = new HashMap<>();
        public TaskMachineLearning taskMl;
        public boolean clean = false;
        public EnumsApi.SnippetExecContext context;

        /**
         * Timeout before terminate a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;

        // fields which are initialized at station
        public String workingPath;

        // key - resource code, value - real file name of resource
        public Map<String, String> realNames;
    }

    public TaskYaml taskYaml = new TaskYaml();

}
