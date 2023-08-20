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

package ai.metaheuristic.ai.yaml.dispatcher;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/16/2021
 * Time: 12:57 AM
 */
@Data
@NoArgsConstructor
public class DispatcherParamsYamlV2 implements BaseParams {

    public final int version=2;

    public static class LongRunningExecContextV2 {
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
    public final List<LongRunningExecContextV2> longRunnings = new ArrayList<>();
}
