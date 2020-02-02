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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:01 PM
 */
@Data
public class PlanParamsYaml implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        final boolean b = plan != null && !S.b(plan.code) && plan.processes != null;
        if (!b) {
            throw new IllegalArgumentException("(b = plan != null && !S.b(plan.code) && plan.processes != null) ");
        }
        for (Process process : plan.processes) {
            if (process.snippet==null) {
                throw new IllegalArgumentException("(process.snippet==null)");
            }
        }

        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetDefForPlan {
        public String code;
        public String params;
        public EnumsApi.SnippetExecContext context = EnumsApi.SnippetExecContext.external;

        public SnippetDefForPlan(String code) {
            this.code = code;
        }

        public SnippetDefForPlan(String code, EnumsApi.SnippetExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variable {
        public EnumsApi.DataSourcing sourcing;
        public GitInfo git;
        public DiskInfo disk;
        public String variable;

        public Variable(EnumsApi.DataSourcing sourcing, String variable) {
            this.sourcing = sourcing;
            this.variable = variable;
        }
    }

    public static class SubProcesses {
        public EnumsApi.PlanProcessExec exec;
        public List<Process> processes;
    }

    @Data
    @ToString
    public static class Process implements Cloneable {

        @SneakyThrows
        public Process clone() {
            //noinspection UnnecessaryLocalVariable
            final Process clone = (Process) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public SnippetDefForPlan snippet;
        public List<SnippetDefForPlan> preSnippets;
        public List<SnippetDefForPlan> postSnippets;

        /**
         * Timeout before terminating a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<Variable> input = new ArrayList<>();
        public final List<Variable> output = new ArrayList<>();
        public List<Meta> metas = new ArrayList<>();
        public SubProcesses subProcesses;

        public Meta getMeta(String key) {
            return MetaUtils.getMeta(metas, key);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControl {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinition {
        public String global;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class PlanYaml {
        public VariableDefinition variables;
        public List<Process> processes = new ArrayList<>();
        public boolean clean = false;
        public String code;
        public List<Meta> metas;
        public AccessControl ac;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternalParams {
        public boolean archived;
        public boolean published;
        public long updatedOn;
        public List<Meta> metas;
    }

    public final int version=8;
    public PlanYaml plan;
    public String originYaml;
    public InternalParams internalParams;

}
