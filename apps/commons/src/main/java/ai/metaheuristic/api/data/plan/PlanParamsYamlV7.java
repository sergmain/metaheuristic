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

package ai.metaheuristic.api.data.plan;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 8:58 PM
 */
@Data
public class PlanParamsYamlV7 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        final boolean b = planYaml != null && planYaml.planCode != null && !planYaml.planCode.isBlank() &&
                planYaml.processes != null;
        if (!b) {
            throw new IllegalArgumentException(
                    "(boolean b = planYaml != null && planYaml.planCode != null && " +
                            "!planYaml.planCode.isBlank() && planYaml.processes != null) ");
        }
        for (ProcessV7 process : planYaml.processes) {
            if (process.type==EnumsApi.ProcessType.FILE_PROCESSING && (process.snippets==null || process.snippets.size()==0)) {
                throw new IllegalArgumentException("(process.type==EnumsApi.ProcessType.FILE_PROCESSING && (process.snippets==null || process.snippets.size()==0))");
            }
        }

        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetDefForPlanV7 {
        public String code;
        public String params;

        public SnippetDefForPlanV7(String code) {
            this.code = code;
        }
    }

    @Data
    @ToString
    public static class ProcessV7 {

        public String name;
        public String code;
        public EnumsApi.ProcessType type;
        public boolean collectResources = false;
        public List<SnippetDefForPlanV7> snippets;
        public List<SnippetDefForPlanV7> preSnippets;
        public List<SnippetDefForPlanV7> postSnippets;
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

        public Meta getMeta(String key) {
            return MetaUtils.getMeta(metas, key);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControlV7 {
        public String groups;
    }

    @Data
    public static class PlanYamlV7 {
        public List<ProcessV7> processes = new ArrayList<>();
        public boolean clean = false;
        public String planCode;
        public List<Meta> metas;
        public AccessControlV7 ac;

        public Meta getMeta(String key) {
            if (metas == null) {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternalParamsV7 {
        public boolean archived;
        public boolean published;
        public long updatedOn;
        public List<Meta> metas = new ArrayList<>();
    }

    public final int version=7;
    public PlanYamlV7 planYaml;
    public String originYaml;
    public InternalParamsV7 internalParams;

}
