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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.data.KbData;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class BackupParams implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scenario {
        public long createdOn;
        public String name;
        public String description;
        public String params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioGroup {
        public long createdOn;
        public String name;
        public String description;
        public List<Scenario> scenarios;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Auth {
        public long createdOn;
        public String code;
        public String params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        public long createdOn;
        public String name;
        public String code;
        public String scheme;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Backup {
        public List<Api> apis;
        public List<Auth> auths;
        public List<ScenarioGroup> scenarioGroups;
    }

    public final Backup backup = new Backup();
}
