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

import ai.metaheuristic.api.v1.data.YamlVersion;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import static ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtilsFactory.DEFAULT_UTILS;
import static ai.metaheuristic.api.v1.data.PlanApiData.PlanInternalParamsYaml;
import static ai.metaheuristic.api.v1.data.PlanApiData.PlanParamsYaml;

public class PlanParamsYamlUtils {

    public static String toString(PlanParamsYaml planYaml) {
        planYaml.version = DEFAULT_UTILS.getVersion();
        return DEFAULT_UTILS.getYaml().dump(planYaml);
    }

    public static PlanParamsYaml to(String s) {
        YamlVersion v = getYamlForVersion().load(s);
        AbstractPlanParamsYamlUtils yamlUtils;
        if (v.version==null) {
            yamlUtils = PlanParamsYamlUtilsFactory.YAML_UTILS_V_1;
        }
        else {
            switch (v.version) {
                case 1:
                    yamlUtils = PlanParamsYamlUtilsFactory.YAML_UTILS_V_1;
                    break;
                case 2:
                    yamlUtils = PlanParamsYamlUtilsFactory.YAML_UTILS_V_2;
                    break;
                case 3:
                    yamlUtils = PlanParamsYamlUtilsFactory.YAML_UTILS_V_3;
                    break;
                default:
                    throw new IllegalStateException("#635.007 Unsupported version of plan: " + v.version);
            }
        }
        Object currPlanParamsYaml = yamlUtils.to(s);
        while (yamlUtils.nextUtil()!=null) {
            //noinspection unchecked
            currPlanParamsYaml = yamlUtils.upgradeTo(currPlanParamsYaml);
        }

        if (!(currPlanParamsYaml instanceof PlanParamsYaml)) {
            throw new IllegalStateException("#635.007 Plan Yaml is null");
        }
        PlanParamsYaml p = (PlanParamsYaml)currPlanParamsYaml;

        if (p.internalParams==null) {
            p.internalParams = new PlanInternalParamsYaml();
        }
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 Plan Yaml is null");
        }
        return p;
    }

    private static Yaml getYamlForVersion() {
        Representer representer = new Representer();
        representer.getPropertyUtils().setSkipMissingProperties(true);
        representer.addClassTag(YamlVersion.class, Tag.MAP);

        Constructor constructor = new Constructor(YamlVersion.class);

        //noinspection UnnecessaryLocalVariable
        Yaml yaml = new Yaml(constructor, representer);
        return yaml;
    }

    
}
