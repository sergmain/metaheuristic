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

package ai.metaheuristic.api.data.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
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
public class SourceCodeParamsYamlV8 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        final boolean b = source != null && !S.b(source.code) && source.processes != null;
        if (!b) {
            throw new CheckIntegrityFailedException("(b = sourceCode != null && !S.b(sourceCode.code) && sourceCode.processes != null) ");
        }
        for (ProcessV8 process : source.processes) {
            if (process.snippet==null) {
                throw new CheckIntegrityFailedException("(process.snippet==null)");
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
        public EnumsApi.SnippetExecContext context = EnumsApi.SnippetExecContext.external;

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
        public String name;

        public VariableV8(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    public static class SubProcessesV8 {
        public EnumsApi.SourceCodeSubProcessLogic logic;
        public List<ProcessV8> processes;
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
        public SnippetDefForPlanV8 snippet;
        public List<SnippetDefForPlanV8> preSnippets;
        public List<SnippetDefForPlanV8> postSnippets;

        /**
         * Timeout before terminating a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<VariableV8> input = new ArrayList<>();
        public final List<VariableV8> output = new ArrayList<>();
        public List<Meta> metas = new ArrayList<>();
        public SubProcessesV8 subProcesses;

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
        public String runtime;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class SourceCodeV8 {
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
        public List<Meta> metas;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OriginV8 {
        public String source;
        public EnumsApi.SourceCodeLang lang;
    }

    public final int version=8;
    public SourceCodeV8 source;
    public final OriginV8 origin = new OriginV8();
    public InternalParamsV8 internalParams;

}
