/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.api_analyzers;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Version 1 of mh-api-analyzers.yaml. Frozen once a version 2 exists; until then it carries the same set
 * of fields as {@link ApiAnalyzersYaml}.
 *
 * @author Sergio Lissner
 * Date: 10/26/2026
 */
@Data
public class ApiAnalyzersYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzerV1 {
        public String name;
        public @Nullable List<String> regex = new ArrayList<>();
        public String timeout;
        public boolean incrementTries;
        public EnumsApi.GateScope scope;
    }

    public @Nullable List<AnalyzerV1> analyzers = new ArrayList<>();
}
