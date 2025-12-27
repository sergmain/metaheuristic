/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.variable;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 4/11/2020
 * Time: 10:03 PM
 */
@Data
@NoArgsConstructor
public class VariableArrayParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Variable {
        public String id;
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        @Nullable
        public GitInfo git;

        @Nullable
        public DiskInfo disk;

        public @Nullable String filename;
        public EnumsApi.DataType dataType;

        public Variable(String id, String name, EnumsApi.DataSourcing sourcing) {
            this.id = id;
            this.name = name;
            this.sourcing = sourcing;
        }

        public Variable(String id, String name, EnumsApi.DataSourcing sourcing, EnumsApi.DataType dataType) {
            this.id = id;
            this.name = name;
            this.sourcing = sourcing;
            this.dataType = dataType;
        }
    }

    public final List<Variable> array = new ArrayList<>();
}
