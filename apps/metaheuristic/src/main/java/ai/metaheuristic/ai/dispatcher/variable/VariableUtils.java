/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import lombok.Data;

import java.util.List;

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
                v.id = pv.globalVariable.id;
                v.name = pv.globalVariable.name;

                DataStorageParams dsp = DataStorageParamsUtils.to(pv.globalVariable.params);
                v.sourcing = dsp.sourcing;
                v.git = dsp.git;
                v.disk = dsp.disk;
                v.realName = pv.globalVariable.filename;
                v.type = EnumsApi.DataType.global_variable;
            }
            else {
                v.id = pv.variable.id;
                v.name = pv.variable.variable;

                DataStorageParams dsp = pv.variable.getParams();
                v.sourcing = dsp.sourcing;
                v.git = dsp.git;
                v.disk = dsp.disk;
                v.realName = pv.variable.originalFilename;
                v.type = EnumsApi.DataType.variable;
            }
            vapy.array.add(v);
        }
        return vapy;
    }

    @Data
    public static class VariableHolder {
        public SimpleVariable variable;
        public GlobalVariable globalVariable;

        public VariableHolder(GlobalVariable globalVariable) {
            this.globalVariable = globalVariable;
        }

        public VariableHolder(SimpleVariable variable) {
            this.variable = variable;
        }

        public String getName() {
            return globalVariable!=null ? globalVariable.name : variable.variable;
        }
    }
}
