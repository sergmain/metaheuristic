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

import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.Data;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class PlanParamsYamlUtils {


    @Data
    public static class YamlVersion {
        public Integer version;
    }

    private static final int CURRENT_PLAN_PARAM_VERSION = 2;

    private static Yaml getYaml() {
        return YamlUtils.init(PlanApiData.PlanParamsYaml.class);
    }

    public static String toString(PlanApiData.PlanParamsYaml planYaml) {
        planYaml.version = CURRENT_PLAN_PARAM_VERSION;
        return getYaml().dump(planYaml);
    }

    public static PlanApiData.PlanParamsYaml to(String s) {
        YamlVersion v = getYamlForVersion().load(s);
        PlanApiData.PlanParamsYaml p;
        if (v.version==null) {
            p = new PlanApiData.PlanParamsYaml();
            p.version = CURRENT_PLAN_PARAM_VERSION;
            p.planYaml = PlanYamlUtils.toPlanYaml(s);
        }
        else {
            //noinspection SwitchStatementWithTooFewBranches
            switch (v.version) {
                case 2:
                    p = getYaml().load(s);
                    break;
                default:
                    throw new IllegalStateException("#635.007 Unsupported version of plan: " + v.version);
            }
        }
        if (p.internalParams==null) {
            p.internalParams = new PlanApiData.PlanInternalParamsYaml();
        }
        if (p.planYaml==null) {
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
