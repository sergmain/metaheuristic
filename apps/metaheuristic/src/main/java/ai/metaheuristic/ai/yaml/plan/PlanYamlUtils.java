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
package ai.metaheuristic.ai.yaml.plan;

import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.Process;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

public class PlanYamlUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(PlanApiData.PlanYaml.class);
    }

    public static String toString(PlanApiData.PlanYaml planYaml) {
        return getYaml().dump(planYaml);
    }

    public static PlanApiData.PlanYaml toPlanYaml(String s) {
        final PlanApiData.PlanYaml p = getYaml().load(s);
        for (Process process : p.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        return p;
    }


}
