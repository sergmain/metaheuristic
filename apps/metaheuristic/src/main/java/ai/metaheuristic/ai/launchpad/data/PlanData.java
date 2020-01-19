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

package ai.metaheuristic.ai.launchpad.data;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.launchpad.Plan;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 4:41 PM
 */
public class PlanData {
    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class PlansForBatchResult extends BaseDataClass {
        public List<Plan> items;
    }
}
