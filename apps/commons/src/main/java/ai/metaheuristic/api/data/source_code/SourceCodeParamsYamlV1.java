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
public class SourceCodeParamsYamlV1 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        final boolean b = source != null && !S.b(source.uid) && source.processes != null;
        if (!b) {
            throw new CheckIntegrityFailedException("(b = sourceCode != null && !S.b(sourceCode.code) && sourceCode.processes != null) ");
        }
        for (ProcessV1 process : source.processes) {
            if (process.snippet==null) {
                throw new CheckIntegrityFailedException("(process.snippet==null)");
            }
        }

        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnippetDefForSourceCodeV1 {
        public String code;
        public String params;
        public EnumsApi.SnippetExecContext context = EnumsApi.SnippetExecContext.external;

        public SnippetDefForSourceCodeV1(String code) {
            this.code = code;
        }

        public SnippetDefForSourceCodeV1(String code, EnumsApi.SnippetExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV1 {
        public EnumsApi.DataSourcing sourcing;
        public GitInfo git;
        public DiskInfo disk;
        public String name;

        public VariableV1(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    public static class SubProcessesV1 {
        public EnumsApi.SourceCodeSubProcessLogic logic;
        public List<ProcessV1> processes;
    }

    @Data
    @ToString
    public static class ProcessV1 implements Cloneable {

        @SneakyThrows
        public ProcessV1 clone() {
            //noinspection UnnecessaryLocalVariable
            final ProcessV1 clone = (ProcessV1) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public SnippetDefForSourceCodeV1 snippet;
        public List<SnippetDefForSourceCodeV1> preSnippets;
        public List<SnippetDefForSourceCodeV1> postSnippets;

        /**
         * Timeout before terminating a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<VariableV1> input = new ArrayList<>();
        public final List<VariableV1> output = new ArrayList<>();
        public List<Meta> metas = new ArrayList<>();
        public SubProcessesV1 subProcesses;

        public Meta getMeta(String key) {
            return MetaUtils.getMeta(metas, key);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControlV1 {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinitionV1 {
        public String global;
        public String runtime;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class SourceCodeV1 {
        public VariableDefinitionV1 variables;
        public List<ProcessV1> processes = new ArrayList<>();
        public boolean clean = false;
        public String uid;
        public List<Meta> metas;
        public AccessControlV1 ac;

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

    public final int version=1;
    public SourceCodeV1 source;

}
