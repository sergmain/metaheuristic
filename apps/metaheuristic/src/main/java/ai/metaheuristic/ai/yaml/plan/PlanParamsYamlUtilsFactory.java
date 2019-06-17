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

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:53 AM
 */
public class PlanParamsYamlUtilsFactory {

    public static final PlanParamsYamlUtilsV1 YAML_UTILS_V_1 = new PlanParamsYamlUtilsV1();
    public static final PlanParamsYamlUtilsV2 YAML_UTILS_V_2 = new PlanParamsYamlUtilsV2();
    public static final PlanParamsYamlUtilsV3 YAML_UTILS_V_3 = new PlanParamsYamlUtilsV3();
    public static final PlanParamsYamlUtilsV3 DEFAULT_UTILS = YAML_UTILS_V_3;


}
