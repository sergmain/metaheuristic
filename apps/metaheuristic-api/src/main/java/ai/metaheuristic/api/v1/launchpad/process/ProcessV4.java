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

package ai.metaheuristic.api.v1.launchpad.process;

import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.Meta;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class ProcessV4 {

    public String name;
    public String code;
    public EnumsApi.ProcessType type;
    public boolean collectResources = false;
    public List<SnippetDefForPlanV4> snippets;
    public List<SnippetDefForPlanV4> preSnippets;
    public List<SnippetDefForPlanV4> postSnippets;
    public boolean parallelExec = false;

    /**
     * Timeout before terminating a process with snippet
     * value in seconds
     * null or 0 mean the infinite execution
     */
    public Long timeoutBeforeTerminate;

    public String inputResourceCode;
    public DataStorageParams outputParams;
    public String outputResourceCode;
    public List<Meta> metas = new ArrayList<>();
    public int order;
    public String outputType;

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
