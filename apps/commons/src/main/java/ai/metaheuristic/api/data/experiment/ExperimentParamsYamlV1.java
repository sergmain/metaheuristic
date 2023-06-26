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

package ai.metaheuristic.api.data.experiment;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.S;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 10:03 PM
 */
@Data
@NoArgsConstructor
public class ExperimentParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (S.b(code)) {
            throw new IllegalArgumentException("(experiment.code==null || experiment.code.isBlank()) ");
        }
        return true;
    }

    public long createdOn;
    public String name;
    public String description;
    public String code;

}
