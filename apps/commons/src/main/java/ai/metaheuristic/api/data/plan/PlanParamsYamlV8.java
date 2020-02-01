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
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * $Date:
 */
@Data
public class PlanParamsYamlV8 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        final boolean b = plan != null && plan.code != null && !plan.code.isBlank() &&
                plan.processes != null;
        if (!b) {
            throw new IllegalArgumentException(
                    "(boolean b = planYaml != null && planYaml.planCode != null && " +
                            "!planYaml.planCode.isBlank() && planYaml.processes != null) ");
        }
        for (ProcessV8 process : plan.processes) {
            if (process.type==EnumsApi.ProcessType.FILE_PROCESSING && (process.snippets==null || process.snippets.size()==0)) {
                throw new IllegalArgumentException("(process.type==EnumsApi.ProcessType.FILE_PROCESSING && (process.snippets==null || process.snippets.size()==0))");
            }
        }

        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetDefForPlanV8 {
        public String code;
        public String params;
        public EnumsApi.SnippetExecContext context;

        public SnippetDefForPlanV8(String code) {
            this.code = code;
        }

        public SnippetDefForPlanV8(String code, EnumsApi.SnippetExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV8 {
        public EnumsApi.DataSourcing sourcing;
        public GitInfo git;
        public DiskInfo disk;
        public String variable;

        public VariableV8(EnumsApi.DataSourcing sourcing, String variable) {
            this.sourcing = sourcing;
            this.variable = variable;
        }
    }

    @Data
    @ToString
    public static class ProcessV8 implements Cloneable {

        @SneakyThrows
        public ProcessV8 clone() {
            //noinspection UnnecessaryLocalVariable
            final ProcessV8 clone = (ProcessV8) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public EnumsApi.ProcessType type;
        public List<SnippetDefForPlanV8> snippets;
        public List<SnippetDefForPlanV8> preSnippets;
        public List<SnippetDefForPlanV8> postSnippets;
        public boolean parallelExec = false;

        /**
         * Timeout before terminating a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<VariableV8> input = new ArrayList<>();
        public final List<VariableV8> output = new ArrayList<>();
        public List<Meta> metas = new ArrayList<>();

        public Meta getMeta(String key) {
            return MetaUtils.getMeta(metas, key);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControlV8 {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinitionV8 {
        public String global;
        public final Map<String, String> inline = new HashMap<>();
    }

    @Data
    public static class PlanYamlV8 {
        public VariableDefinitionV8 variables;
        public List<ProcessV8> processes = new ArrayList<>();
        public boolean clean = false;
        public String code;
        public List<Meta> metas;
        public AccessControlV8 ac;

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
    public static class InternalParamsV8 {
        public boolean archived;
        public boolean published;
        public long updatedOn;
        public List<Meta> metas = new ArrayList<>();
    }

    public final int version=8;
    public PlanYamlV8 plan;
    public String originYaml;
    public InternalParamsV8 internalParams;

}
