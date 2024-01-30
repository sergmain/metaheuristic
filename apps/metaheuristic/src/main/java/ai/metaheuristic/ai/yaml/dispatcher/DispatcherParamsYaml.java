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

package ai.metaheuristic.ai.yaml.dispatcher;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 4:24 PM
 */
@Data
@NoArgsConstructor
public class DispatcherParamsYaml implements BaseParams {

    public final int version=2;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LongRunningExecContext {
        public Long taskId;
        public Long execContextId;
    }

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    // values of the follow lists are Uid of SourceCode
    public final List<String> batches = new ArrayList<>();
    public final List<String> experiments = new ArrayList<>();
    public final List<LongRunningExecContext> longRunnings = new ArrayList<>();
}
