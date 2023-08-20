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

package ai.metaheuristic.ai.yaml.account;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 7/15/2023
 * Time: 12:01 AM
 */
@SuppressWarnings("FieldMayBeStatic")
@Data
public class AccountParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyV1 {
        public String name;
        public String value;
    }

    public final List<ApiKeyV1> apiKeys = new ArrayList<>();

    public String openaiKey;
    public String language = "en";

}
