/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
@NoArgsConstructor
public class DispatcherLookupParamsYaml implements BaseParams {

    public final int version=2;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    public static class AssetManager {
        @EqualsAndHashCode.Include
        public String url;
        public String username;
        public String password;
        @Nullable
        public String publicKey;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherLookup {
        // fields, which are specific to concrete installation
        // string representation of ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule
        @Nullable
        public String taskProcessingTime;

        // common fields
        public boolean disabled;
        public String url;
        public boolean signatureRequired;
        @Nullable
        public String publicKey;
        @Nullable
        public Enums.DispatcherLookupType lookupType;
        public EnumsApi.AuthType authType;

        public String restUsername;
        public String restPassword;
        public String assetManagerUrl;
        public int priority=0;
    }

    public final List<DispatcherLookup> dispatchers = new ArrayList<>();
    public final List<AssetManager> assetManagers = new ArrayList<>();

}
