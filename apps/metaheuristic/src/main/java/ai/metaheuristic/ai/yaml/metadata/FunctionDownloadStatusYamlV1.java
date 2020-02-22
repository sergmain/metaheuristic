/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 10/3/2019
 * Time: 4:51 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FunctionDownloadStatusYamlV1 implements BaseParams {

    public final int version=1;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusV1 {
        public Enums.FunctionState functionState;
        public String code;
        public String dispatcherUrl;
        public EnumsApi.FunctionSourcing sourcing;
        public boolean verified;
    }

    public List<StatusV1> statuses = new ArrayList<>();

    @Override
    public boolean checkIntegrity() {
        return true;
    }
}
