/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.api.data.atlas;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 8/3/2019
 * Time: 12:57 PM
 */

@Data
@NoArgsConstructor
public class AtlasTaskParamsYamlV1 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        if (taskId==null) {
            throw new IllegalArgumentException("(taskId==null)");
        }
        return true;
    }

    public Long taskId;
    public String taskParams;
    public int execState;

    public Long completedOn;
    public boolean completed;
    public Long assignedOn;
    public String typeAsString;

    public String metrics;
    public String functionExecResults;

    public final int version = 1;
}
