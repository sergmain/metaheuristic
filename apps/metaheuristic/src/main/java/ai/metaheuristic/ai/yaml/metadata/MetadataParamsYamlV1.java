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

package ai.metaheuristic.ai.yaml.metadata;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.LinkedHashMap;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 1:32 AM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Data
    @NoArgsConstructor
    @ToString
    public static class DispatcherInfoV1 {

        // right now this field isn't used
        public String value;
        public String code;
        public String processorId;
        public String sessionId;
    }

    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public LinkedHashMap<String, DispatcherInfoV1> dispatcher = new LinkedHashMap<>();
}