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

package ai.metaheuristic.api.data.plan;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.launchpad.process.ProcessV4;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 8:58 PM
 */
@Data
public class PlanParamsYamlV4 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class PlanYamlV4 {
        public List<ProcessV4> processes = new ArrayList<>();
        public boolean clean = false;
        public List<Meta> metas;

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

    public final int version=4;
    public PlanYamlV4 planYaml;
    public PlanApiData.PlanInternalParamsYaml internalParams;

}
