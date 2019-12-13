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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.launchpad.process.ProcessV6;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 8:58 PM
 */
@Data
public class PlanParamsYamlV6 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        final boolean b = planYaml != null && planYaml.planCode != null && !planYaml.planCode.isBlank() &&
                planYaml.processes != null;
        if (!b) {
            throw new IllegalArgumentException(
                    "(boolean b = planYaml != null && planYaml.planCode != null && " +
                            "!planYaml.planCode.isBlank() && planYaml.processes != null) ");
        }
        for (ProcessV6 process : planYaml.processes) {
            if (process.type==EnumsApi.ProcessType.FILE_PROCESSING && (process.snippets==null || process.snippets.size()==0)) {
                throw new IllegalArgumentException("(process.type==EnumsApi.ProcessType.FILE_PROCESSING && (process.snippets==null || process.snippets.size()==0))");
            }
        }

        return true;
    }

    @Data
    public static class AccessControlV6 {
        public String group;
    }

    @Data
    public static class PlanYamlV6 {
        public List<ProcessV6> processes = new ArrayList<>();
        public boolean clean = false;
        public String planCode;
        public List<Meta> metas;
        public AccessControlV6 ac;

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

    public final int version=6;
    public PlanYamlV6 planYaml;
    public PlanApiData.PlanInternalParamsYaml internalParams;

}
