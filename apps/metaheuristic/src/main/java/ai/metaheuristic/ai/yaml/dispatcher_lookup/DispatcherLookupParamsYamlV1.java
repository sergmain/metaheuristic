/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
@NoArgsConstructor
public class DispatcherLookupParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetV1 {
        public String url;
        public String username;
        public String password;
        @Nullable
        public String publicKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherLookupV1 {
        // fields, which are specific to concrete installation
        // actually, it's a schedule
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

        public String restPassword;
        public String restUsername;

        @Nullable
        public AssetV1 asset;

        // must be deleted when DispatcherLookup will be based on ParamsYaml versioning
        // this field is only for compatibility
        @Deprecated
        public boolean acceptOnlySignedFunctions = false;

        public int priority=0;
    }

    public final List<DispatcherLookupV1> dispatchers = new ArrayList<>();
}
