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

package ai.metaheuristic.ai.mhbp.yaml.backup;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class BackupParamsV1 implements BaseParams  {

    public final int version=1;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioV1 {
        public long createdOn;
        public String name;
        public String description;
        public String params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioGroupV1 {
        public long createdOn;
        public String name;
        public String description;
        public final List<ScenarioV1> scenarios = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthV1 {
        public long createdOn;
        public String code;
        public String params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiV1 {
        public long createdOn;
        public String name;
        public String code;
        public String scheme;
    }

    @Data
    @NoArgsConstructor
    public static class BackupV1 {
        public final List<ApiV1> apis = new ArrayList<>();
        public final List<AuthV1> auths = new ArrayList<>();
        public final List<ScenarioGroupV1> scenarioGroups = new ArrayList<>();
    }

    public final BackupV1 backup = new BackupV1();
}
