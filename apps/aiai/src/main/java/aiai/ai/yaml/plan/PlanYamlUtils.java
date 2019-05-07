/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml.plan;

import aiai.apps.commons.yaml.YamlUtils;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class PlanYamlUtils {

    private Yaml yamlPlanYaml;

    // TODO 2018.09.12. so, snakeYaml isn't thread-safe or it was a side-effect?
    private static final Object syncObj = new Object();

    public PlanYamlUtils() {
        yamlPlanYaml = YamlUtils.init(PlanYaml.class);
    }

    public String toString(PlanYaml planYaml) {
        synchronized (syncObj) {
            return yamlPlanYaml.dump(planYaml);
        }
    }

    public PlanYaml toPlanYaml(String s) {
        synchronized (syncObj) {
            return yamlPlanYaml.load(s);
        }
    }
}
