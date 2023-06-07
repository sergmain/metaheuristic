/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Serge
 * Date: 7/30/2020
 * Time: 10:48 AM
 */
public class VariableUtils {

    public static VariableArrayParamsYaml toVariableArrayParamsYaml(List<VariableHolder> permutedVariables) {
        VariableArrayParamsYaml vapy = new VariableArrayParamsYaml();
        for (VariableHolder pv : permutedVariables) {
            VariableArrayParamsYaml.Variable v = new VariableArrayParamsYaml.Variable();
            if (pv.globalVariable!=null) {
                v.id = pv.globalVariable.id.toString();
                v.name = pv.globalVariable.variable;

                DataStorageParams dsp = DataStorageParamsUtils.to(pv.globalVariable.params);
                v.sourcing = dsp.sourcing;
                v.git = dsp.git;
                v.disk = dsp.disk;
                v.filename = pv.globalVariable.filename;
                v.dataType = EnumsApi.DataType.global_variable;
            }
            else {
                Variable variable = Objects.requireNonNull(pv.variable);
                v.id = variable.id.toString();
                v.name = variable.name;

                DataStorageParams dsp = variable.getDataStorageParams();
                v.sourcing = dsp.sourcing;
                v.git = dsp.git;
                v.disk = dsp.disk;
                v.filename = variable.filename;
                v.dataType = EnumsApi.DataType.variable;
            }
            vapy.array.add(v);
        }
        return vapy;
    }

    @NonNull
    public static String getNameForVariableInArray() {
        return S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());
    }

    public static String permutationAsString(VariableData.Permutation ps) throws JsonProcessingException {
        String s = JsonUtils.getMapper().writeValueAsString(ps);
        return s;
    }

    public static VariableData.Permutation asStringAsPermutation(String json) throws JsonProcessingException {
        VariableData.Permutation p = JsonUtils.getMapper().readValue(json, VariableData.Permutation.class);
        return p;
    }

    @Nullable
    public static String getParentContext(String taskContextId) {
        if (!taskContextId.contains(",")) {
            if (taskContextId.indexOf(ContextUtils.CONTEXT_DIGIT_SEPARATOR)!=-1) {
                throw new IllegalStateException("(taskContextId.indexOf(ContextUtils.CONTEXT_DIGIT_SEPARATOR)!=-1)");
            }
            return null;
        }
        return taskContextId.substring(0, taskContextId.lastIndexOf(ContextUtils.CONTEXT_DIGIT_SEPARATOR)).strip();
    }

    @Data
    @NoArgsConstructor
    public static class VariableHolder {
        @Nullable
        public Variable variable;

        @Nullable
        public SimpleGlobalVariable globalVariable;

        public VariableHolder(SimpleGlobalVariable globalVariable) {
            this.globalVariable = globalVariable;
        }

        public VariableHolder(Variable variable) {
            this.variable = variable;
        }

        public String getName() {
            return globalVariable!=null ? globalVariable.variable : Objects.requireNonNull(variable).name;
        }

        @Nullable
        public String getFilename() {
            return globalVariable!=null ? globalVariable.filename : Objects.requireNonNull(variable).filename;
        }

        public boolean notInited() {
            return (variable==null || !variable.inited) && globalVariable==null;
        }
    }
}
