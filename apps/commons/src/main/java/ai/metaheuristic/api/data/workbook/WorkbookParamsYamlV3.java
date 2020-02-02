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

package ai.metaheuristic.api.data.workbook;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class WorkbookParamsYamlV3 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class WorkbookYamlV3 {
        public Map<String, List<String>> variables = new HashMap<>();

        public boolean preservePoolNames;
    }

    public final int version = 3;
    public WorkbookYamlV3 workbookYaml = new WorkbookYamlV3();
    public String graph;
}
