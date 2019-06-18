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

package ai.metaheuristic.api.v1.data.task;

import ai.metaheuristic.api.v1.data.BaseParams;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@Data
public class TaskParamsYaml implements BaseParams {

    @Data
    public static class TaskYaml {
        public Map<String, List<String>> inputResourceCodes = new HashMap<>();
        public SnippetApiData.SnippetConfig snippet;
        public List<SnippetApiData.SnippetConfig> preSnippet;
        public List<SnippetApiData.SnippetConfig> postSnippet;
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
    public TaskYaml taskYaml = new TaskYaml();

}
