/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 9:12 AM
 */
@Data
@NoArgsConstructor
public class DispatcherLookupParamsYamlV3 implements BaseParams {

    public final int version=3;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of="url")
    public static class AssetManagerV3 {
        public String url;
        public String username;
        public String password;
        public String publicKey;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherLookupV3 {
        // fields, which are specific to concrete installation
        // string representation of ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule
        public String taskProcessingTime;

        // common fields
        public boolean disabled;
        public String url;
        public boolean signatureRequired;
        public String publicKey;
        public Enums.DispatcherLookupType lookupType;
        public Enums.AuthType authType;

        public String restUsername;
        public String restPassword;
        public String assetManagerUrl;
        public int priority=0;
        public boolean dataSource;
    }

    public final List<DispatcherLookupV3> dispatchers = new ArrayList<>();
    public final List<AssetManagerV3> assetManagers = new ArrayList<>();

}
